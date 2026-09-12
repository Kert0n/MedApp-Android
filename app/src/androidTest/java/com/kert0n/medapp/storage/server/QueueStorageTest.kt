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
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.queueRepository
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.queue.Delivery
import com.kert0n.medapp.queue.PackageState
import com.kert0n.medapp.queue.RefusalReason
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
import com.kert0n.medapp.queue.Take
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
import org.junit.Assert.assertFalse
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

    private val snapshotJson = """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"17.000000","quantityUnitId":"${TABLETS.id}",
         "formTypeId":"${TABLET_FORM.id}","medKitId":"$HOME_KIT","version":4},
         "reservations":{"total":"4.000000","mine":"4.000000","version":2}}
    """

    private val snapshot: PackageSnapshotNetworkDTO =
        medAppJson.decodeFromString(PackageSnapshotNetworkDTO.serializer(), snapshotJson)

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

        val taken = (storage.take(operation, null, at) as Take.Sending).operation

        val request = requireNotNull(taken.prepared)
        assertEquals(SyncOperationStatus.SENDING, taken.status)
        assertEquals(ResourceVersion(3), request.drugVersion)
        assertEquals(tablets("20"), request.quantityBefore)
        assertTrue(request.body!!.contains("\"drugVersion\":3"))
        assertEquals(listOf(taken.id), storage.ready(at.plusSeconds(600)).map { it.id })
    }

    /** Свежее состояние ложится первым, и запрос везёт его версию и остаток, а не те, что лежали в строке. */
    @Test
    fun takingWithAFreshSnapshotAppliesItAndFreezesItsPreconditions() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)

        val taken = (storage.take(operation, snapshot, at) as Take.Sending).operation

        val request = requireNotNull(taken.prepared)
        assertEquals(ResourceVersion(4), request.drugVersion)
        assertEquals(ResourceVersion(2), request.claimsVersion)
        assertEquals(tablets("17"), request.quantityBefore)
        assertEquals(tablets("4"), request.mineBefore)
        assertEquals(tablets("17"), requireNotNull(database.packageRepository().find(PACK)).quantity)
    }

    @Test
    fun takingAgainDoesNotRebuildTheRequest() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        val first = (storage.take(operation, null, at) as Take.Sending).operation.prepared
        // Версия пачки ушла вперёд — а замороженный запрос остался с прежней (PLAN E2).
        val moved = pack(quantity = tablets("20"), form = TABLET_FORM)
        database.packages().applyServerSnapshot(
            moved.toStorageEntity(PackageSyncState(PACK, ResourceVersion(9), ResourceVersion(1), at)), at
        )

        val second = (storage.take(operation, null, at.plusSeconds(60)) as Take.Sending).operation.prepared

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
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.Applied(PackageState.Present(snapshot)), at.plusSeconds(1))

        val row = requireNotNull(database.packages().find(PACK))
        val pkg = row.toDomain(VOCABULARY)
        assertEquals(tablets("17"), pkg.quantity)
        assertEquals(ResourceVersion(4), row.pack.syncState().version)
        assertEquals(ResourceVersion(2), row.pack.syncState().claimsVersion)
        assertNotNull(pkg.claims)
        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.APPLIED, stored.operation.status)
        assertEquals(1, stored.operation.attempts)
        assertEquals(IntakeAccounting.REMOTE_APPLIED, requireNotNull(database.intakes().findEntity(INTAKE)).accounting)
        assertEquals(IntakeStatus.TAKEN, requireNotNull(database.intakes().findEntity(INTAKE)).status)
        assertTrue(storage.ready(at.plusSeconds(600)).isEmpty())
    }

    @Test
    fun settlingRetryKeepsTheOperationPendingWithTheError() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.Retry("обрыв"), at.plusSeconds(1))

        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.PENDING, stored.operation.status)
        assertEquals("обрыв", stored.operation.lastError)
        assertEquals(tablets("20"), requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).quantity)
    }

    @Test
    fun packageGoneFromTheServerIsArchivedLocally() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("20"), INTAKE), at)
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.Applied(PackageState.Gone), at.plusSeconds(1))

        val pkg = requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY)
        assertEquals(tablets("0"), pkg.quantity)
        assertEquals(false, pkg.suppliesStock)
    }

    @Test
    fun accessLostMarksThePackageAndClosesTheOperation() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.AccessLost, at.plusSeconds(1))

        val pkg = requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY)
        assertEquals(com.kert0n.medapp.domain.pack.Package.Access.LOST, pkg.access)
        assertNull(pkg.claims)
        assertTrue(storage.ready(at.plusSeconds(600)).isEmpty())
    }

    @Test
    fun dependentOperationWaitsForItsDependency() = runTest {
        val release = Uuid.parse("00000000-0000-4000-8000-000000000092")
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("0")), at)
        database.syncOperations().enqueue(release, PackageSyncCommand.ReleaseClaim(PACK), at, dependsOn = setOf(operation))

        assertEquals(listOf(operation), storage.ready(at.plusSeconds(600)).map { it.id })
        storage.take(operation, null, at)
        storage.settle(operation, Delivery.Applied(PackageState.Present(snapshot)), at)
        assertEquals(listOf(release), storage.ready(at.plusSeconds(600)).map { it.id })
    }

    /** «Устарело» — не закрытие: снимок ложится, запрос сбрасывается, операция снова ждёт под тем же номером. */
    @Test
    fun staleAppliesTheSnapshotDropsTheRequestAndLeavesTheOperationPending() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        val frozen = (storage.take(operation, null, at) as Take.Sending).operation.prepared
        // Исход неизвестен — факт принадлежит этому запросу, а не операции.
        storage.settle(operation, Delivery.Retry("ответ потерян", outcomeUnknown = true), at)
        val taken = (storage.take(operation, null, at.plusSeconds(1)) as Take.Sending).operation
        assertTrue(taken.outcomeUnknown)

        storage.settle(operation, Delivery.Stale(snapshot), at.plusSeconds(1))

        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.PENDING, stored.operation.status)
        assertNull(stored.operation.prepared)
        // Запрос сброшен — сброшен и факт о нём; счёт попыток остаётся у операции как вход задержки.
        assertFalse(stored.operation.outcomeUnknown)
        assertEquals(1, stored.operation.attempts)
        assertEquals(tablets("17"), requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).quantity)
        // Заново — уже по свежему состоянию, а не по прежнему запросу.
        val again = (storage.take(operation, null, at.plusSeconds(2)) as Take.Sending).operation.prepared
        assertEquals(ResourceVersion(4), again!!.drugVersion)
        assertEquals(ResourceVersion(3), frozen!!.drugVersion)
    }

    /** Отказ родителя отказывает зависимых: им нужен был эффект, которого не будет; расход виден как непринятый. */
    @Test
    fun refusalCascadesToDependentsAndMarksTheIntakeRefused() = runTest {
        val release = Uuid.parse("00000000-0000-4000-8000-000000000092")
        database.intakes().upsert(
            unplannedIntake(takenAmount = dose("3")).toIntakeStorageEntity(
                IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)
            )
        )
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("0")), at)
        database.syncOperations().enqueue(release, PackageSyncCommand.ReleaseClaim(PACK), at, dependsOn = setOf(operation))
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.Refused(RefusalReason.INSUFFICIENT, PackageState.Present(snapshot)), at.plusSeconds(1))

        val refused = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        val dependent = requireNotNull(database.syncOperations().find(release)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.REFUSED, refused.operation.status)
        assertEquals("INSUFFICIENT", refused.operation.lastError)
        assertEquals(SyncOperationStatus.REFUSED, dependent.operation.status)
        assertEquals("SUPERSEDED", dependent.operation.lastError)
        assertEquals(IntakeAccounting.REMOTE_REFUSED, requireNotNull(database.intakes().findEntity(INTAKE)).accounting)
        assertEquals(tablets("17"), requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).quantity)
        assertTrue(storage.ready(at.plusSeconds(600)).isEmpty())
    }

    /** Единицу пачки сменили на сервере: расход закрывается отказом при взятии, не тревожа сервер. */
    @Test
    fun takingClosesTheOperationWhenThePreparationRefusesIt() = runTest {
        database.intakes().upsert(
            unplannedIntake(takenAmount = dose("3")).toIntakeStorageEntity(
                IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)
            )
        )
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        val inMillilitres = medAppJson.decodeFromString(
            PackageSnapshotNetworkDTO.serializer(),
            snapshotJson.replace(TABLETS.id.toString(), com.kert0n.medapp.fixture.MILLILITRES.id.toString())
        )

        val take = storage.take(operation, inMillilitres, at)

        assertEquals(Take.Closed(Delivery.Refused(RefusalReason.UNIT_CHANGED, PackageState.None)), take)
        val stored = requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable
        assertEquals(SyncOperationStatus.REFUSED, stored.operation.status)
        assertEquals(IntakeAccounting.REMOTE_REFUSED, requireNotNull(database.intakes().findEntity(INTAKE)).accounting)
        assertEquals(com.kert0n.medapp.fixture.millilitres("17"), requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).quantity)
    }

    /** Полученный ответ записан до применения: он в базе, операция готова к закрытию без сети. */
    @Test
    fun anAnswerIsKeptWithTheOperationUntilItIsSettled() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, null, at)

        storage.answered(operation, RawResponse(200, snapshotJson), at.plusSeconds(1))
        storage.defer(operation, "словарь не знает единицу", at.plusSeconds(2), notBefore = at.plusSeconds(4))

        val stored = (requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation
        assertEquals(SyncOperationStatus.ANSWERED, stored.status)
        assertEquals(RawResponse(200, snapshotJson), stored.answer)
        assertEquals(1, stored.attempts)
        assertEquals(listOf(operation), storage.ready(at.plusSeconds(600)).map { it.id })
        assertNull(storage.take(operation, null, at.plusSeconds(3)))

        storage.settle(operation, Delivery.Applied(PackageState.Present(snapshot)), at.plusSeconds(4))
        val settled = (requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation
        assertEquals(SyncOperationStatus.APPLIED, settled.status)
        assertNull(settled.answer)
    }

    /** Готовность — одно определение в запросе: срок, зависимости и порядок по пачке. */
    @Test
    fun readinessIsTheTermTheDependenciesAndTheOrderWithinThePackage() = runTest {
        val second = Uuid.parse("00000000-0000-4000-8000-000000000093")
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        database.syncOperations().enqueue(second, PackageSyncCommand.Consume(PACK, dose("1"), second), at)
        storage.take(operation, null, at)

        storage.settle(operation, Delivery.Retry("обрыв", notBefore = at.plusSeconds(30)), at)

        // Первая ждёт срока, вторая ждёт первую: до срока готовых нет, после — только первая.
        assertTrue(storage.ready(at.plusSeconds(10)).isEmpty())
        assertEquals(listOf(operation), storage.ready(at.plusSeconds(31)).map { it.id })
    }

    /** Запоздалый снимок свежий не перекрывает: меньшая версия большую не откатывает (PLAN E1). */
    @Test
    fun anOlderSnapshotDoesNotOverwriteANewerOne() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, null, at)
        val older = medAppJson.decodeFromString(
            PackageSnapshotNetworkDTO.serializer(),
            snapshotJson.replace("\"version\":4", "\"version\":2").replace("17.000000", "19.000000")
        )

        storage.settle(operation, Delivery.Applied(PackageState.Present(older)), at.plusSeconds(1))

        val row = requireNotNull(database.packages().find(PACK))
        assertEquals(tablets("20"), row.toDomain(VOCABULARY).quantity)
        assertEquals(ResourceVersion(3), row.pack.syncState().version)
        assertEquals(SyncOperationStatus.APPLIED, (requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation.status)
    }

    /** Закрытие одно: закрытую операцию второй исход не переписывает и следствий не оставляет. */
    @Test
    fun aClosedOperationIsNotClosedAgain() = runTest {
        database.syncOperations().enqueue(operation, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE), at)
        storage.take(operation, null, at)
        storage.settle(operation, Delivery.Applied(PackageState.Present(snapshot)), at.plusSeconds(1))

        storage.settle(operation, Delivery.AccessLost, at.plusSeconds(2))

        val stored = (requireNotNull(database.syncOperations().find(operation)).toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation
        assertEquals(SyncOperationStatus.APPLIED, stored.status)
        assertEquals(com.kert0n.medapp.domain.pack.Package.Access.AVAILABLE, requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).access)
        assertNull(storage.take(operation, null, at.plusSeconds(3)))
    }
}
