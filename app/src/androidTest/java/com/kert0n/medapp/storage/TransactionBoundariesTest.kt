package com.kert0n.medapp.storage

import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.courseRepository
import com.kert0n.medapp.fixture.intakeRepository
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.prescription
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.unplannedIntake
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.storage.course.ActivePackageAssignmentStorageEntity
import com.kert0n.medapp.storage.course.CourseRoomRepository
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeOutcome
import com.kert0n.medapp.storage.intake.IntakeRoomRepository
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.course.CourseReallocation
import com.kert0n.medapp.storage.course.CourseStorageEntity
import com.kert0n.medapp.storage.pack.PackageAdjustment
import com.kert0n.medapp.storage.pack.PackageRoomRepository
import com.kert0n.medapp.storage.server.QueuedCommand
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Связанные изменения сохраняются атомарно: откат не оставляет ни отдельного расхода, ни
 * факта, ни брони, а план и запись эпизода не бывают в базе поодиночке (PLAN F5).
 */
class TransactionBoundariesTest {

    private lateinit var database: MedAppDatabase
    private lateinit var packages: PackageRoomRepository
    private lateinit var courses: CourseRoomRepository
    private lateinit var intakes: IntakeRoomRepository

    private val operation: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000091")
    private val movementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")
    private val otherMovementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000082")
    private val at: Instant = Instant.parse("2026-09-10T12:00:00Z")

    private val paracetamol = pack(quantity = tablets("20"))

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        packages = database.packageRepository()
        courses = database.courseRepository()
        intakes = database.intakeRepository()
        database.medKits().upsert(medKit().toMedKitStorageEntity())
        database.medKits().upsert(medKit(id = SHARED_KIT, name = "Дача").toMedKitStorageEntity())
        packages.add(paracetamol)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun draft(): CourseDraft.Activation {
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        return CourseDraft.Activation(plan, courseRecord(prescription = plan.prescription))
    }

    @Test
    fun activationWritesPlanAndRecordTogether() = runTest {
        val activation = draft()
        courses.activate(activation, planned = listOf(plannedIntake()), at = at)

        assertNotNull(courses.findPlan(COURSE))
        assertNotNull(courses.findRecord(COURSE))
        assertEquals(COURSE, courses.courseHolding(PACK))
        assertEquals(IntakeStatus.PLANNED, requireNotNull(intakes.find(INTAKE)).status)
    }

    /**
     * Занятая пачка отвергается назначением, и тогда не остаётся ни плана, ни записи: их
     * поодиночке в базе не бывает (PLAN F5).
     */
    @Test
    fun activationOnAnOccupiedPackageLeavesNothingBehind() = runTest {
        val other: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000052")
        database.courses().upsertCourse(activeCourse(id = other).toCourseStorageEntity())
        database.courses().assignPackage(ActivePackageAssignmentStorageEntity(PACK, other))

        val failure = runCatching { courses.activate(draft(), at = at) }.exceptionOrNull()

        assertNotNull(failure)
        assertNull(courses.findPlan(COURSE))
        assertNull(courses.findRecord(COURSE))
        assertEquals(other, courses.courseHolding(PACK))
    }

    @Test
    fun closingRemovesThePlanAndKeepsTheRecord() = runTest {
        val activation = draft()
        courses.activate(activation, planned = listOf(plannedIntake()), at = at)

        val closed = activation.record.close(CourseRecord.Outcome.CANCELLED, LATER)
        courses.close(
            record = closed,
            cancelled = listOf(plannedIntake().cancel(LATER)),
            at = LATER
        )

        assertNull(courses.findPlan(COURSE))
        val record = requireNotNull(courses.findRecord(COURSE))
        assertFalse(record.isOpen)
        assertEquals(CourseRecord.Outcome.CANCELLED, record.outcome)
        assertNull(courses.courseHolding(PACK))
        assertEquals(IntakeStatus.CANCELLED, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(activation.record.prescription, record.prescription)
    }

    @Test
    fun confirmingAnIntakeWritesFactStockAndMovementTogether() = runTest {
        courses.activate(draft(), planned = listOf(plannedIntake()), at = at)

        val applied = intakes.record(
            IntakeOutcome(
                intake = plannedIntake().confirm(paracetamol, dose("2"), LATER),
                expected = setOf(IntakeStatus.PLANNED, IntakeStatus.MISSED),
                sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED),
            )
        )

        assertTrue(applied)
        assertEquals(IntakeStatus.TAKEN, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(
            IntakeAccounting.LOCAL_APPLIED,
            requireNotNull(intakes.syncStateOf(INTAKE)).accounting
        )
    }

    /** Повтор уже совершённого подтверждения ничего не списывает второй раз (PLAN D6). */
    @Test
    fun repeatingAConfirmationChangesNothing() = runTest {
        courses.activate(draft(), planned = listOf(plannedIntake()), at = at)
        val outcome = {
            IntakeOutcome(
                intake = plannedIntake().confirm(paracetamol, dose("2"), LATER),
                expected = setOf(IntakeStatus.PLANNED),
                sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)
            )
        }
        assertTrue(intakes.record(outcome()))

        assertFalse(intakes.record(outcome()))
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
    }

    /**
     * Откат не оставляет отдельного расхода, факта или брони: несуществующая пачка в команде
     * очереди роняет всю транзакцию (PLAN F5).
     */
    @Test
    fun rollbackLeavesNeitherFactNorStockNorQueuedCommand() = runTest {
        courses.activate(draft(), planned = listOf(plannedIntake()), at = at)
        val clash = QueuedCommand(operation, PackageSyncCommand.Consume(PACK, dose("2"), INTAKE))
        database.syncOperations().enqueue(operation, PackageSyncCommand.Delete(PACK), at)

        val failure = runCatching {
            intakes.record(
                IntakeOutcome(
                    intake = plannedIntake().confirm(paracetamol, dose("2"), LATER),
                    expected = setOf(IntakeStatus.PLANNED),
                    sync = IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation),
                    command = clash
                )
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(IntakeStatus.PLANNED, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(emptyList<StockMovement>(), database.stockMovements().ofPackage(PACK).map { it.toDomain() })
        assertEquals(1, database.syncOperations().all().size)
    }

    /** Внеплановому приёму строки заранее нет: он заводится вставкой вместе с расходом (F5). */
    @Test
    fun unplannedIntakeIsWrittenTogetherWithTheStock() = runTest {
        assertTrue(intakes.record(unplannedOutcome()))

        assertTrue(intakes.find(INTAKE) is UnplannedIntake)
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
    }

    /** Повтор внепланового приёма узнаётся по тождеству и второй раз не списывает (D6). */
    @Test
    fun repeatingAnUnplannedIntakeChangesNothing() = runTest {
        assertTrue(intakes.record(unplannedOutcome()))

        assertFalse(intakes.record(unplannedOutcome()))
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
    }

    /**
     * Приём, подтверждённый между чтением и концом лечения, отменой не затирается: списание уже
     * случилось, и история должна это помнить (PLAN D6, F2).
     */
    @Test
    fun closingDoesNotCancelAnIntakeAnsweredInTheMeantime() = runTest {
        val activation = draft()
        courses.activate(activation, planned = listOf(plannedIntake()), at = at)
        assertTrue(intakes.record(confirmedOutcome()))

        courses.close(
            record = activation.record.close(CourseRecord.Outcome.CANCELLED, LATER),
            cancelled = listOf(plannedIntake().cancel(LATER)),
            at = LATER
        )

        assertEquals(IntakeStatus.TAKEN, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(
            IntakeAccounting.LOCAL_APPLIED,
            requireNotNull(intakes.syncStateOf(INTAKE)).accounting
        )
    }

    /** Расход не трогает обвязку доставки: версия предусловия у пачки остаётся прежней (E3). */
    @Test
    fun spendingKeepsThePackagePreconditions() = runTest {
        val sync = PackageSyncState(
            PACK,
            version = ResourceVersion(5),
            claimsVersion = ResourceVersion(2),
            syncedAt = at
        )
        packages.add(paracetamol, sync)
        courses.activate(draft(), planned = listOf(plannedIntake()), at = at)

        assertTrue(intakes.record(confirmedOutcome()))

        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(sync, requireNotNull(database.packages().find(PACK)).pack.syncState())
    }

    /**
     * Расход, уехавший командой, локального остатка не трогает: там лежит подтверждённое
     * сервером, а незакрытую команду сворачивает очередь (PLAN E1).
     */
    @Test
    fun spendingThatLeavesByCommandDoesNotTouchTheLocalAmount() = runTest {
        courses.activate(draft(), planned = listOf(plannedIntake()), at = at)

        assertTrue(
            intakes.record(
                IntakeOutcome(
                    intake = plannedIntake().confirm(paracetamol, dose("2"), LATER),
                    expected = setOf(IntakeStatus.PLANNED),
                    sync = IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation),
                    command = QueuedCommand(
                        operation,
                        PackageSyncCommand.Consume(PACK, dose("2"), INTAKE)
                    )
                )
            )
        )

        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(1, database.syncOperations().all().size)
    }

    /** Пачки, из которой принято, уже нет — тогда и факт не записывается: половины расхода не бывает. */
    @Test
    fun anIntakeFromAPackageThatIsGoneIsNotRecorded() = runTest {
        val outcome = IntakeOutcome(
            intake = unplannedIntake(takenPackageId = OTHER_PACK, takenAmount = dose("2")),
            expected = emptySet(),
            sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)
        )

        assertFalse(intakes.record(outcome))
        assertNull(intakes.find(INTAKE))
    }

    /** Закрытый план не возвращается пересчётом, прочитавшим курс до закрытия (PLAN D5, F5). */
    @Test
    fun adjustmentDoesNotResurrectAClosedPlan() = runTest {
        val activation = draft()
        courses.activate(activation, at = at)
        courses.close(
            record = activation.record.close(CourseRecord.Outcome.CANCELLED, LATER),
            at = LATER
        )

        packages.adjust(
            PackageAdjustment.Recount(PACK, tablets("17"), movementId),
            reallocation = CourseReallocation(activation.course, activation.course.revision),
            at = LATER
        )

        assertNull(database.courses().findPlan(COURSE))
        assertEquals(tablets("17"), requireNotNull(packages.find(PACK)).quantity)
    }

    /**
     * Активация уничтожает черновик, а не прячет его: экран, оставшийся открытым, не возвращает
     * начатое лечение в состояние черновика.
     */
    @Test
    fun aDraftIsNotWrittenOverAStartedTreatment() = runTest {
        val activation = draft()
        val stale = course(title = "Старый черновик")
        courses.activate(activation, at = at)

        assertFalse(courses.saveDraft(stale))
        assertNull(courses.findDraft(COURSE))
        assertNotNull(courses.findPlan(COURSE))
    }

    /**
     * Конец лечения уничтожает план, но запись эпизода остаётся навсегда: «плана нет» само по
     * себе не значит «черновик ещё можно сохранить».
     */
    @Test
    fun aDraftDoesNotResurrectAFinishedEpisode() = runTest {
        val activation = draft()
        courses.activate(activation, at = at)
        courses.close(activation.record.close(CourseRecord.Outcome.COMPLETED, LATER), at = LATER)

        assertFalse(courses.saveDraft(course(title = "Старый черновик")))
        assertNull(courses.findDraft(COURSE))
        assertEquals(
            CourseRecord.Outcome.COMPLETED,
            requireNotNull(courses.findRecord(COURSE)).outcome
        )
    }

    /**
     * Пересчёт выделений из устаревшего состава не проходит молча: вокруг него в той же
     * транзакции уже записан расход, обеспечение которого он и считал.
     */
    @Test
    fun aStaleReallocationAbortsTheWholeTransaction() = runTest {
        val activation = draft()
        courses.activate(activation, planned = listOf(plannedIntake()), at = at)
        val stale = CourseReallocation(activation.course, activation.course.revision)
        database.courses().updateAllocations(
            activation.course.toCourseStorageEntity().let {
                CourseStorageEntity(
                    id = it.id, doseAmount = it.doseAmount, unitId = it.unitId, formId = it.formId,
                    start = it.start, endInclusive = it.endInclusive, daysOfWeek = it.daysOfWeek,
                    zone = it.zone, revision = it.revision + 1, createdAt = it.createdAt,
                    updatedAt = LATER
                )
            },
            emptyList(),
            activation.course.revision
        )

        val failure = runCatching {
            packages.adjust(
                PackageAdjustment.Recount(PACK, tablets("17"), movementId),
                reallocation = stale,
                at = LATER
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(emptyList<StockMovement>(), database.stockMovements().ofPackage(PACK).map { it.toDomain() })
    }

    /** В минус пачка не уходит, и в историю попадает то, что действительно ушло. */
    @Test
    fun disposingMoreThanThereIsRecordsWhatActuallyLeft() = runTest {
        packages.adjust(
            PackageAdjustment.Disposal(
                PACK,
                tablets("50"),
                StockMovement.Disposal.Reason.DAMAGED,
                movementId
            ),
            at = LATER
        )

        val disposal = database.stockMovements().ofPackage(PACK).single().toDomain()
        assertEquals(tablets("20"), (disposal as StockMovement.Disposal).amount)
        assertEquals(Package.Lifecycle.ARCHIVED, requireNotNull(packages.find(PACK)).lifecycle)
    }

    /**
     * Правится название эпизода и только оно: экран, загрузивший открытую запись, не возвращает
     * законченное лечение в открытое состояние (PLAN D5).
     */
    @Test
    fun renamingDoesNotReopenAClosedRecord() = runTest {
        val activation = draft()
        courses.activate(activation, at = at)
        courses.close(activation.record.close(CourseRecord.Outcome.COMPLETED, LATER), at = LATER)

        assertTrue(courses.rename(COURSE, "Другое название", note = null))

        val record = requireNotNull(courses.findRecord(COURSE))
        assertEquals("Другое название", record.title)
        assertEquals(CourseRecord.Outcome.COMPLETED, record.outcome)
        assertEquals(activation.record.prescription, record.prescription)
    }

    private fun confirmedOutcome() = IntakeOutcome(
        intake = plannedIntake().confirm(paracetamol, dose("2"), LATER),
        expected = setOf(IntakeStatus.PLANNED),
        sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)
    )

    private fun unplannedOutcome() = IntakeOutcome(
        intake = unplannedIntake(takenAmount = dose("2")),
        expected = emptySet(),
        sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)
    )

    @Test
    fun recountWritesMovementAndStockTogether() = runTest {
        packages.adjust(PackageAdjustment.Recount(PACK, tablets("17"), movementId), at = LATER)

        assertEquals(tablets("17"), requireNotNull(packages.find(PACK)).quantity)
        val recount = database.stockMovements().ofPackage(PACK).single().toDomain()
        assertEquals(tablets("20"), (recount as StockMovement.Recount).before)
        assertEquals(tablets("17"), recount.after)
    }

    /**
     * След пересчёта берёт «было» из базы, а не из снимка, с которым пришёл вызывающий: экран мог
     * прочитать пачку до чужой записи, и тогда история назвала бы неверное число.
     */
    @Test
    fun recountRecordsTheAmountThatWasActuallyThere() = runTest {
        packages.adjust(PackageAdjustment.Recount(PACK, tablets("18"), movementId), at = LATER)

        packages.adjust(PackageAdjustment.Recount(PACK, tablets("15"), otherMovementId), at = LATER)

        val second = database.stockMovements().ofPackage(PACK)
            .map { it.toDomain() }
            .filterIsInstance<StockMovement.Recount>()
            .single { it.id == otherMovementId }
        assertEquals(tablets("18"), second.before)
        assertEquals(tablets("15"), requireNotNull(packages.find(PACK)).quantity)
    }

    @Test
    fun disposalToZeroArchivesAndKeepsTheTrace() = runTest {
        packages.adjust(
            PackageAdjustment.Disposal(
                PACK,
                tablets("20"),
                StockMovement.Disposal.Reason.EXPIRED,
                movementId
            ),
            at = LATER
        )

        val archived = requireNotNull(packages.find(PACK))
        assertEquals(Package.Lifecycle.ARCHIVED, archived.lifecycle)
        assertEquals(1, database.stockMovements().ofPackage(PACK).size)
    }

    @Test
    fun transferMovesThePackageAndRecordsBothEnds() = runTest {
        packages.adjust(
            PackageAdjustment.Transfer(PACK, medKit(id = SHARED_KIT, name = "Дача"), movementId),
            at = LATER
        )

        assertEquals(SHARED_KIT, requireNotNull(packages.find(PACK)).medKitId)
        val transfer = database.stockMovements().ofPackage(PACK).single().toDomain()
        assertEquals(StockMovement.Transfer::class, transfer::class)
    }

    /** Упавшая команда очереди откатывает и остаток, и движение: половины пересчёта не бывает. */
    @Test
    fun failedAdjustmentLeavesNeitherStockNorMovement() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Delete(PACK), at)

        val failure = runCatching {
            packages.adjust(
                PackageAdjustment.Recount(PACK, tablets("4"), movementId),
                command = QueuedCommand(operation, PackageSyncCommand.CorrectStock(PACK, tablets("4"))),
                at = LATER
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
        assertEquals(emptyList<StockMovement>(), database.stockMovements().ofPackage(PACK).map { it.toDomain() })
    }
}
