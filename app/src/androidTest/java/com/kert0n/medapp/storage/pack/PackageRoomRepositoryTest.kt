package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.queueRepository
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.SyncOperationStatus
import com.kert0n.medapp.storage.course.ActivePackageAssignmentStorageEntity
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationRoomRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Репозиторий отдаёт домен, а не строки: пачка собирается из трёх таблиц, а оценку количества
 * даёт свёртка незакрытых команд очереди (PLAN E1, H1).
 */
class PackageRoomRepositoryTest {

    private lateinit var database: MedAppDatabase
    private lateinit var repository: PackageRoomRepository
    private lateinit var queue: SyncOperationRoomRepository

    private val today = LocalDate.of(2027, 3, 1)
    private val at: Instant = Instant.parse("2026-09-10T12:00:00Z")
    private val operation: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000091")
    private val reconcile: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000092")
    private val later: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000093")

    private val paracetamol = pack(quantity = tablets("20"))

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        repository = database.packageRepository()
        queue = database.queueRepository()
        database.medKits().upsert(medKit().toMedKitStorageEntity())
        repository.add(paracetamol)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savedPackageIsObservedAsDomain() = runTest {
        val observed = requireNotNull(repository.observe(PACK).first())
        assertEquals(paracetamol.facts, observed.facts)
        assertEquals(tablets("20"), observed.quantity)
    }

    @Test
    fun withoutQueueTheAmountIsTheConfirmedOne() = runTest {
        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("20"), availability.effective)
        assertEquals(tablets("20"), availability.availableToMe)
        assertEquals(tablets("20"), availability.freeForAnyone)
    }

    /** Незакрытый расход вычитается из подтверждённого остатка ровно один раз (PLAN E1). */
    @Test
    fun unclosedConsumeIsProjectedOnce() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("17"), availability.effective)
    }

    @Test
    fun settledOperationStopsAffectingTheAmount() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.DONE)

        assertEquals(
            tablets("20"),
            requireNotNull(repository.observeAvailability(PACK).first()).effective
        )
    }

    /** Расход, чей ответ потерялся, из числа не выпадает: устройство знает, что отправило (E1). */
    @Test
    fun operationWithALostAnswerStillCounts() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.PENDING, lastError = "обрыв", at = at, attempted = true)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("17"), availability.effective)
        assertEquals(tablets("17"), availability.freeForAnyone)
    }

    /**
     * Ожидающая сверка отсекает всё до своего среза: неустановленный расход уже вошёл в
     * пересчитанное число, а более новый расход ложится поверх него (PLAN E3).
     */
    @Test
    fun pendingReconcileCutsOffWhatItAlreadyCounts() = runTest {
        val counted = queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.PENDING, lastError = "обрыв", at = at, attempted = true)
        queue.enqueue(reconcile, PackageSyncCommand.Reconcile(PACK, tablets("10"), counted.sequence), at)
        queue.enqueue(later, PackageSyncCommand.Consume(PACK, dose("2"), OTHER_INTAKE), at)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("8"), availability.effective)
    }

    @Test
    fun claimsOfOthersReduceWhatIsAvailableToMe() = runTest {
        repository.saveClaims(PACK, Claims(total = BigDecimal("8"), mine = BigDecimal("3")))

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("15"), availability.availableToMe)
    }

    @Test
    fun claimsAreDroppedWhenAccessIsLost() = runTest {
        repository.saveClaims(PACK, Claims(total = BigDecimal("8")))
        repository.saveClaims(PACK, null)

        assertNull(requireNotNull(repository.observe(PACK).first()).claims)
    }

    /** Занятое активным курсом вычитается из свободного, а доступное мне не трогает. */
    @Test
    fun activeCourseAllocationIsSubtractedFromTheFreePart() = runTest {
        givenActiveCourseTaking(doses = 4)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("20"), availability.availableToMe)
        assertEquals(tablets("12"), availability.freeForAnyone)
    }

    @Test
    fun hasFreeKeepsOnlyPackagesWithSomethingLeftOver() = runTest {
        val other = pack(id = OTHER_PACK, name = "Ибупрофен", quantity = tablets("8"))
        repository.add(other)
        givenActiveCourseTaking(doses = 10)

        val found = repository.list(
            PackageQuery(filter = PackageQuery.Filter.HasFree),
            today
        ).first().map { it.name }

        assertEquals(listOf("Ибупрофен"), found)
    }

    /**
     * Пачка, к которой утрачен доступ, свободной не считается — хотя её количество осталось
     * известным, а брони с неё сняты вместе с доступом.
     */
    @Test
    fun packageOutOfReachIsNotCountedAsFree() = runTest {
        repository.saveClaims(PACK, Claims(total = BigDecimal("8")))

        assertTrue(repository.loseAccess(PACK))

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(tablets("0"), availability.freeForAnyone)
        // Брони снимаются вместе с доступом: их больше не существует, а не «их не видно».
        assertNull(requireNotNull(repository.observe(PACK).first()).claims)
        assertEquals(
            emptyList<String>(),
            repository.list(PackageQuery(filter = PackageQuery.Filter.HasFree), today).first().map { it.name }
        )
    }

    /**
     * Переименование правит описание и только его: остаток, обвязка синхронизации и брони
     * остаются нынешними, хотя экран загрузил пачку до чужой записи.
     */
    @Test
    fun describingDoesNotWriteBackAStaleAmount() = runTest {
        val sync = PackageSyncState(PACK, version = ResourceVersion(5), syncedAt = at)
        repository.applyServerSnapshot(paracetamol.correctTo(tablets("11")), sync, at)

        val renamed = paracetamol.facts.let { it.copy(shared = it.shared.copy(name = "Панадол")) }

        assertTrue(repository.describe(PACK, renamed))

        val described = requireNotNull(repository.find(PACK))
        assertEquals("Панадол", described.name)
        assertEquals(tablets("11"), described.quantity)
        assertEquals(sync, requireNotNull(database.packages().find(PACK)).pack.syncState())
    }

    @Test
    fun snapshotKeepsLocalDetailsAndPreconditions() = runTest {
        val sync = PackageSyncState(PACK, version = ResourceVersion(5), claimsVersion = ResourceVersion(2), syncedAt = at)
        repository.applyServerSnapshot(paracetamol.correctTo(tablets("11")), sync, at)

        assertEquals(tablets("11"), requireNotNull(repository.find(PACK)).quantity)
        assertEquals(sync, requireNotNull(database.packages().find(PACK)).pack.syncState())
        assertEquals(paracetamol.addedAt, requireNotNull(repository.find(PACK)).addedAt)
    }

    private suspend fun givenActiveCourseTaking(doses: Int) {
        val plan = activeCourse(sources = listOf(source(PACK, doses)))
        database.courses().saveCourse(
            plan.toCourseStorageEntity(),
            emptyList(),
            plan.medicine.toSourceStorageEntities(COURSE)
        )
        database.courses().assignPackage(ActivePackageAssignmentStorageEntity(PACK, COURSE))
    }
}
