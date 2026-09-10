package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
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

    private val paracetamol = pack(quantity = tablets("20"))

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        repository = database.packageRepository()
        queue = database.queueRepository()
        database.medKits().upsert(medKit().toMedKitStorageEntity())
        repository.save(paracetamol)
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
        assertEquals(EffectiveAmount.Known(tablets("20")), availability.amount)
        assertEquals(tablets("20"), availability.availableToMe)
        assertEquals(tablets("20"), availability.freeForAnyone)
    }

    /** Незакрытый расход вычитается из подтверждённого остатка ровно один раз (PLAN E1). */
    @Test
    fun unclosedConsumeIsProjectedOnce() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(EffectiveAmount.Known(tablets("17")), availability.amount)
    }

    @Test
    fun settledOperationStopsAffectingTheAmount() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.DONE)

        assertEquals(
            EffectiveAmount.Known(tablets("20")),
            requireNotNull(repository.observeAvailability(PACK).first()).amount
        )
    }

    /** Неустановленный исход делает число неизвестным, а не нулевым (PLAN E1, D4). */
    @Test
    fun operationWithUnknownOutcomeMakesTheAmountUnknown() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.NEEDS_RECOUNT)

        val availability = requireNotNull(repository.observeAvailability(PACK).first())
        assertEquals(EffectiveAmount.Unknown, availability.amount)
        assertTrue(availability.requiresRecount)
        assertNull(availability.freeForAnyone)
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
        repository.save(other)
        givenActiveCourseTaking(doses = 10)

        val found = repository.list(
            PackageQuery(filter = PackageQuery.Filter.HasFree),
            today
        ).first().map { it.name }

        assertEquals(listOf("Ибупрофен"), found)
    }

    /** «Неизвестно» — это не «есть свободное»: пачка, требующая сверки, из списка уходит. */
    @Test
    fun packageThatNeedsRecountIsNotCountedAsFree() = runTest {
        queue.enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        queue.settle(operation, SyncOperationStatus.NEEDS_RECOUNT)

        val found = repository.list(PackageQuery(filter = PackageQuery.Filter.HasFree), today)
        assertEquals(emptyList<String>(), found.first().map { it.name })
    }

    @Test
    fun snapshotKeepsLocalDetailsAndPreconditions() = runTest {
        val sync = PackageSyncState(PACK, version = 5, claimsVersion = 2, syncedAt = at)
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
