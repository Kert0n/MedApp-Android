package com.kert0n.medapp.feature.intake

import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeRejected
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.FIRST_PLANNED_AT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.courseRepository
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.intakeRepository
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.queueStorage
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.course.CourseRoomRepository
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeOutcome
import com.kert0n.medapp.storage.intake.IntakeRoomRepository
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageRoomRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Подтверждение приёма — одно действие без сети: факт, остаток, прогресс, обеспечение, конец
 * эпизода и команды ложатся вместе, а связь только пробуется (PLAN D5, D6, F5).
 */
class IntakeConfirmationTest {

    private lateinit var database: MedAppDatabase
    private lateinit var courses: CourseRoomRepository
    private lateinit var intakes: IntakeRoomRepository
    private lateinit var packages: PackageRoomRepository
    private lateinit var confirmation: IntakeConfirmation

    private val now: Instant = Instant.parse("2027-03-10T12:00:00Z")
    private val third: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000063")

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        courses = database.courseRepository()
        intakes = database.intakeRepository()
        packages = database.packageRepository()
        val queue = database.queueStorage()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        confirmation = IntakeConfirmation(intakes, courses, packages, queue, QueueService(queue), clock)
        packages.add(pack(quantity = tablets("20")))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun publishHomeKit() =
        database.medKits().upsert(medKit(publication = MedKit.Publication.PUBLISHED).toMedKitStorageEntity())

    /** Курс на [totalDoses] доз по две таблетки из PACK; выделено столько же, но не больше пяти. */
    private suspend fun activate(totalDoses: Int = 7, planned: List<CourseIntake> = listOf(plannedIntake())) {
        val plan = activeCourse(totalDoses = totalDoses, sources = listOf(source(PACK, minOf(5, totalDoses))))
        courses.activate(CourseDraft.Activation(plan, courseRecord(prescription = plan.prescription)), planned)
    }

    private fun planned(id: Uuid, slot: ScheduledOccurrence) =
        plannedIntake(id = id, plannedAt = slot.at, scheduledOn = slot.localDate, scheduledTime = slot.localTime)

    private suspend fun miss(intake: CourseIntake) =
        assertTrue(intakes.record(IntakeOutcome(intake.miss(LATER), expected = setOf(IntakeStatus.PLANNED))))

    private suspend fun commands(): List<SyncCommand> = database.syncOperations().all()
        .map { (it.toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation.command }

    @Test
    fun ownKitSpendsLocallyAndReallocatesThePackage() = runTest {
        activate()

        val confirmed = confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).getOrThrow()

        assertEquals(IntakeAccounting.LOCAL_APPLIED, confirmed.accounting)
        assertEquals(IntakeStatus.TAKEN, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
        // Выделено было пять доз (10 таблеток), ушло две таблетки: осталось четыре дозы.
        assertEquals(Doses(4), requireNotNull(courses.findPlan(COURSE)).sources.single().allocatedDoses)
        assertEquals(0, database.syncOperations().all().size)
    }

    @Test
    fun sharedKitQueuesTheConsumptionWithTheNewClaim() = runTest {
        publishHomeKit()
        activate()

        val confirmed = confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).getOrThrow()

        assertEquals(IntakeAccounting.PENDING, confirmed.accounting)
        // Локально лежит подтверждённое сервером; незакрытый расход сворачивает очередь.
        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
        val consume = commands().single() as PackageSyncCommand.Consume
        assertEquals(INTAKE, consume.intakeId)
        assertEquals(0, BigDecimal("8").compareTo(requireNotNull(consume.claimAfter).amount))
    }

    @Test
    fun theLastDoseClosesTheEpisodeAndReleasesTheClaim() = runTest {
        publishHomeKit()
        activate(totalDoses = 1)

        val confirmed = confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).getOrThrow()

        assertTrue(confirmed.episodeClosed)
        assertEquals(CourseRecord.Outcome.COMPLETED, requireNotNull(courses.findRecord(COURSE)).outcome)
        assertNull(courses.findPlan(COURSE))
        assertNull(courses.courseHolding(PACK))
        val (consume, release) = commands()
        assertTrue(requireNotNull((consume as PackageSyncCommand.Consume).claimAfter).isZero)
        assertEquals(PackageSyncCommand.ReleaseClaim(PACK), release)
    }

    /**
     * Пропуск утром растянул курс на день, и третий пункт материализовался; поздний ответ по
     * пропущенному сдвигает конец назад, и лишний плановый пункт убирается — он не факт.
     */
    @Test
    fun aLateAnswerRemovesTheSurplusPlannedOccurrence() = runTest {
        val slots = schedule().next(schedule().beginning, 3)
        val first = planned(INTAKE, slots[0])
        activate(totalDoses = 2, planned = listOf(first, planned(OTHER_INTAKE, slots[1]), planned(third, slots[2])))
        miss(first)

        confirmation.confirm(INTAKE, PACK, dose("2"), slots[0].at).getOrThrow()

        assertEquals(IntakeStatus.TAKEN, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(IntakeStatus.PLANNED, requireNotNull(intakes.find(OTHER_INTAKE)).status)
        assertNull(intakes.find(third))
    }

    /** Закрытый план ответов не принимает: пропущенный остаётся пропущенным, списания нет. */
    @Test
    fun aClosedEpisodeRefusesTheAnswer() = runTest {
        activate()
        miss(plannedIntake())
        courses.close(requireNotNull(courses.findRecord(COURSE)).close(CourseRecord.Outcome.CANCELLED, LATER))

        val refused = confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).exceptionOrNull()

        assertEquals(IntakeRejected.Reason.EPISODE_CLOSED, (refused as IntakeRejected).reason)
        assertEquals(IntakeStatus.MISSED, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(tablets("20"), requireNotNull(packages.find(PACK)).quantity)
    }

    /** Когда приняли, называет человек: вчерашний факт по открытому эпизоду записывается как есть. */
    @Test
    fun yesterdaysIntakeIsAcceptedWhileTheEpisodeIsOpen() = runTest {
        activate()
        val yesterday = now.minusSeconds(24 * 60 * 60)

        confirmation.confirm(INTAKE, PACK, dose("2"), yesterday).getOrThrow()

        assertEquals(yesterday, requireNotNull(intakes.find(INTAKE)?.taken).at)
    }

    /**
     * Пачку теперь считают в миллилитрах, а курс — в таблетках: акт по пачке проходит, но пункт
     * измерен другой единицей, и приём отвергается целиком, а не роняет сценарий.
     */
    @Test
    fun aPackageCountedInAnotherUnitIsRefusedAndWritesNothing() = runTest {
        activate()
        packages.add(pack(id = OTHER_PACK, quantity = millilitres("100")))

        val refused = confirmation.confirm(INTAKE, OTHER_PACK, dose(millilitres("2")), FIRST_PLANNED_AT).exceptionOrNull()

        assertEquals(IntakeRejected.Reason.UNIT_MISMATCH, (refused as IntakeRejected).reason)
        assertEquals(IntakeStatus.PLANNED, requireNotNull(intakes.find(INTAKE)).status)
        assertEquals(millilitres("100"), requireNotNull(packages.find(OTHER_PACK)).quantity)
    }

    /** Двойное нажатие: второй раз отвечает записанным и второй раз не списывает (PLAN D6). */
    @Test
    fun repeatingTheConfirmationSpendsOnce() = runTest {
        activate()
        confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).getOrThrow()

        val repeated = confirmation.confirm(INTAKE, PACK, dose("2"), FIRST_PLANNED_AT).getOrThrow()

        assertFalse(repeated.episodeClosed)
        assertEquals(IntakeAccounting.LOCAL_APPLIED, repeated.accounting)
        assertEquals(tablets("18"), requireNotNull(packages.find(PACK)).quantity)
    }
}
