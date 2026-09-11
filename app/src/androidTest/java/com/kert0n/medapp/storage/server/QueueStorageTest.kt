package com.kert0n.medapp.storage.server

import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.unplannedIntake
import com.kert0n.medapp.fixture.queueRepository
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.queue.Delivery
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.toStorageEntity as toIntakeStorageEntity
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY

/**
 * Хранилище очереди для работника: заморозка запроса с предусловиями пачки, закрытие с
 * применением снимка и учётом приёма — одной транзакцией каждое (PLAN E1, E2, F5).
 */
class QueueStorageTest {

    private lateinit var database: MedAppDatabase
    private val storage get() = database.queueRepository()

    private val operation: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000091")
    private val at: Instant = Instant.parse("2026-09-10T12:00:00Z")

    private val snapshot: PackageSnapshotNetworkDTO = medAppJson.decodeFromString(
        PackageSnapshotNetworkDTO.serializer(),
        """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"17.000000","quantityUnitId":"${TABLETS.id}",
         "formTypeId":"${TABLET_FORM.id}","medKitId":"$HOME_KIT","version":4},
         "reservations":{"total":"4.000000","mine":"4.000000","version":2}}
        """
    )

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        database.medKits().upsert(medKit().toMedKitStorageEntity())
        val paracetamol = pack(quantity = tablets("20"), form = TABLET_FORM)
        database.packages().save(
            paracetamol.toStorageEntity(PackageSyncState(PACK, ResourceVersion(3), ResourceVersion(1), at)),
            paracetamol.toDetailsStorageEntity()
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun takingFreezesTheRequestWithThePackagesPreconditionsAndMarksSending() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)

        val taken = requireNotNull(storage.take(operation, at))

        val request = requireNotNull(taken.prepared)
        assertEquals(SyncOperationStatus.SENDING, taken.status)
        assertEquals(ResourceVersion(3), request.drugVersion)
        assertEquals(tablets("20"), request.quantityBefore)
        assertTrue(request.body!!.contains("\"version\":3"))
        assertEquals(listOf(taken.id), storage.ready().map { it.id })
    }

    @Test
    fun takingAgainDoesNotRebuildTheRequest() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        val first = requireNotNull(storage.take(operation, at)).prepared
        // Версия пачки ушла вперёд — а замороженный запрос остался с прежней (PLAN E2).
        val moved = pack(quantity = tablets("20"), form = TABLET_FORM)
        database.packages().applyServerSnapshot(
            moved.toStorageEntity(PackageSyncState(PACK, ResourceVersion(9), ResourceVersion(1), at)), at
        )

        val second = requireNotNull(storage.take(operation, at.plusSeconds(60))).prepared

        assertEquals(first, second)
        assertEquals(ResourceVersion(3), second!!.drugVersion)
    }

    @Test
    fun settlingDoneAppliesTheSnapshotClosesTheOperationAndAccountsTheIntake() = runTest {
        database.intakes().upsert(
            unplannedIntake(takenAmount = dose("3")).toIntakeStorageEntity(
                IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)
            )
        )
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, at)

        storage.settle(operation, Delivery.Done(snapshot), at.plusSeconds(1))

        val row = requireNotNull(database.packages().find(PACK))
        val pkg = row.toDomain(VOCABULARY)
        assertEquals(tablets("17"), pkg.quantity)
        assertEquals(ResourceVersion(4), row.pack.syncState().version)
        assertEquals(ResourceVersion(2), row.pack.syncState().claimsVersion)
        assertNotNull(pkg.claims)
        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.DONE, stored.operation.status)
        assertEquals(1, stored.operation.attempts)
        assertEquals(IntakeAccounting.REMOTE_APPLIED, requireNotNull(database.intakes().findEntity(INTAKE)).accounting)
        assertEquals(IntakeStatus.TAKEN, requireNotNull(database.intakes().findEntity(INTAKE)).status)
        assertTrue(storage.ready().isEmpty())
    }

    @Test
    fun settlingRetryKeepsTheOperationPendingWithTheError() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, at)

        storage.settle(operation, Delivery.Retry("обрыв"), at.plusSeconds(1))

        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.PENDING, stored.operation.status)
        assertEquals("обрыв", stored.operation.lastError)
        assertEquals(tablets("20"), requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).quantity)
    }

    @Test
    fun packageGoneFromTheServerIsArchivedLocally() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("20"), INTAKE), at)
        storage.take(operation, at)

        storage.settle(operation, Delivery.Done(snapshot = null), at.plusSeconds(1))

        val pkg = requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY)
        assertEquals(tablets("0"), pkg.quantity)
        assertEquals(false, pkg.suppliesStock)
    }

    @Test
    fun accessLostMarksThePackageAndClosesTheOperation() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, at)

        storage.settle(operation, Delivery.AccessLost, at.plusSeconds(1))

        val pkg = requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY)
        assertEquals(com.kert0n.medapp.domain.pack.Package.Access.LOST, pkg.access)
        assertNull(pkg.claims)
        assertTrue(storage.ready().isEmpty())
    }

    @Test
    fun dependentOperationWaitsForItsDependency() = runTest {
        val release = Uuid.parse("00000000-0000-4000-8000-000000000092")
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("0")), at)
        database.syncOperations().enqueue(release, PackageSyncCommand.ReleaseClaim(PACK), at, dependsOn = setOf(operation))

        assertEquals(listOf(operation), storage.ready().map { it.id })
        storage.take(operation, at)
        storage.settle(operation, Delivery.Done(snapshot), at)
        assertEquals(listOf(release), storage.ready().map { it.id })
    }
}
