package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.queue.pack.toPreparedRequest
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppHttpClient
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.network.value.VocabularyMiss
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.network.value.VocabularyStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Работник отправляет замороженным запросом, читает исход и отпускает. Неопределённости нет:
 * обрыв — повтор тем же запросом, отказ — чтение истины снимком (PLAN E2, E3).
 */
class QueueWorkerTest {

    private val now: Instant = EARLIER.plusSeconds(3600)

    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val consume = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE)

    private val sync = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("4"))

    private val snapshotJson = """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"17.000000","quantityUnitId":"${TABLETS.id}",
         "formTypeId":"${TABLET_FORM.id}","medKitId":"$HOME_KIT","version":4},
         "reservations":{"total":"4.000000","mine":"4.000000","version":2}}
    """

    private val snapshot: PackageSnapshotNetworkDTO =
        medAppJson.decodeFromString(PackageSnapshotNetworkDTO.serializer(), snapshotJson)

    /** Очередь в памяти: операции, их запросы и исходы — ровно то, что видит работник. */
    private class Storage(operations: List<SyncOperation>) : QueueStorage {
        val operations = operations.associateBy { it.id }.toMutableMap()
        val settled = mutableListOf<Pair<Uuid, Delivery>>()
        val unreadable = mutableListOf<StoredSyncOperation.Unreadable>()
        var frozen = 0

        override suspend fun ready(): List<StoredSyncOperation> =
            operations.values
                .filter { it.status == SyncOperationStatus.PENDING || it.status == SyncOperationStatus.SENDING }
                .sortedBy { it.sequence }
                .map<SyncOperation, StoredSyncOperation> { StoredSyncOperation.Readable(it) } + unreadable

        override suspend fun take(id: Uuid, at: Instant): SyncOperation? {
            val operation = operations[id] ?: return null
            val prepared = operation.prepared ?: run {
                frozen++
                (operation.command as PackageSyncCommand).toPreparedRequest(
                    operation.id, PackageSyncState(PACK, ResourceVersion(3)), tablets("20"), null, at
                )
            }
            return operation.with(status = SyncOperationStatus.SENDING, prepared = prepared).also { operations[id] = it }
        }

        override suspend fun <T> transaction(block: suspend () -> T): T = block()

        override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation =
            error("работник команд не ставит")

        override suspend fun settle(id: Uuid, outcome: Delivery, at: Instant) {
            settled += id to outcome
            val operation = operations.getValue(id)
            operations[id] = operation.with(
                status = when (outcome) {
                    is Delivery.Done -> SyncOperationStatus.DONE
                    is Delivery.Retry -> SyncOperationStatus.PENDING
                    Delivery.AccessLost -> SyncOperationStatus.ACCESS_LOST
                },
                attempts = operation.attempts + 1,
                lastTriedAt = at
            )
        }

        private fun SyncOperation.with(
            status: SyncOperationStatus = this.status,
            prepared: PreparedRequest? = this.prepared,
            attempts: Int = this.attempts,
            lastTriedAt: Instant? = this.lastTriedAt
        ) = SyncOperation(
            id, command, sequence, createdAt, payloadVersion, prepared, groupId, dependsOn,
            status, attempts, lastError, lastTriedAt
        )
    }

    private class Transport(private val answer: (PreparedRequest) -> ApiResult<String?>) : QueueTransport {
        val sent = mutableListOf<PreparedRequest>()
        var snapshots = 0
        var snapshotAnswer: ApiResult<PackageSnapshotNetworkDTO>? = null

        override suspend fun send(request: PreparedRequest): ApiResult<String?> {
            sent += request
            return answer(request)
        }

        override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> {
            snapshots++
            return requireNotNull(snapshotAnswer) { "снимок в этом тесте не ожидался" }
        }
    }

    private class Store : VocabularyStore {
        var refreshed = 0
        override suspend fun snapshot() = Vocabulary(listOf(TABLETS), listOf(TABLET_FORM))
        override suspend fun save(units: List<com.kert0n.medapp.domain.value.QuantityUnit>, forms: List<com.kert0n.medapp.domain.value.DosageForm>) {
            refreshed++
        }
    }

    private val store = Store()

    private fun resolver(online: Boolean) = VocabularyResolver(
        store,
        MedAppApi(
            medAppHttpClient(
                MockEngine { request ->
                    if (!online) throw java.io.IOException("связи нет")
                    respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
                "https://medapp.test",
                retryDelay = { delayMillis(false) { 0L } }
            )
        )
    )

    private fun operation(
        command: SyncCommand = consume,
        id: Uuid = INTAKE,
        sequence: Long = 0,
        attempts: Int = 0,
        lastTriedAt: Instant? = null
    ) = SyncOperation(
        id = id, command = command, sequence = sequence, createdAt = EARLIER, payloadVersion = 1,
        attempts = attempts, lastTriedAt = lastTriedAt
    )

    private fun worker(storage: Storage, transport: Transport, online: Boolean = true) =
        QueueWorker(storage, transport, resolver(online), clock)

    @Test
    fun pendingOperationIsSentAndSettledDoneWithTheSnapshot() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = Transport { ApiResult.Success(snapshotJson) }

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        assertEquals("POST", transport.sent.single().method)
        assertEquals("/v1/drugs/$PACK/intakes", transport.sent.single().path)
        assertEquals(Delivery.Done(snapshot), storage.settled.single().second)
        assertEquals(SyncOperationStatus.DONE, storage.operations.getValue(INTAKE).status)
        assertNull(report.retryAt)
    }

    @Test
    fun conflictOnSyncReadsTheSnapshotAndCloses() = runTest {
        // 409 у `sync` — «уже применено»: штатный исход, истина читается снимком (PLAN B4).
        val storage = Storage(listOf(operation(sync)))
        val transport = Transport { ApiResult.Failure(ApiFailure.Conflict) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        assertEquals(1, transport.snapshots)
        assertEquals(Delivery.Done(snapshot, refusal = null), storage.settled.single().second)
        assertTrue(transport.sent.single().path.endsWith("/sync/$INTAKE"))
    }

    @Test
    fun refusalByPreconditionClosesWithTheRefusalNamedAndTheTruthRead() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = Transport { ApiResult.Failure(ApiFailure.PreconditionFailed) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        worker(storage, transport).drain()

        val outcome = storage.settled.single().second as Delivery.Done
        assertEquals(snapshot, outcome.snapshot)
        assertNotNull(outcome.refusal)
    }

    @Test
    fun retryAfterIsHonouredAndNothingElseIsSentInThatPass() = runTest {
        val storage = Storage(listOf(operation(sequence = 0), operation(id = OTHER_PACK, sequence = 1)))
        val transport = Transport { ApiResult.Failure(ApiFailure.TooManyRequests(30.seconds)) }

        val report = worker(storage, transport).drain()

        assertEquals(1, transport.sent.size)
        assertEquals(now.plusSeconds(30), report.retryAt)
        assertEquals(0, report.settled)
        assertEquals(SyncOperationStatus.PENDING, storage.operations.getValue(INTAKE).status)
    }

    @Test
    fun aBrokenConnectionRetriesWithTheVerySamePreparedRequest() = runTest {
        // Запрос заморожен при первой отправке; на повторе он не пересобирается, даже если
        // версия пачки с тех пор изменилась бы (PLAN E2, E3).
        val storage = Storage(listOf(operation()))
        var broken = true
        val transport = Transport { if (broken) ApiResult.Failure(ApiFailure.OutcomeUnknown) else ApiResult.Success(snapshotJson) }

        val first = worker(storage, transport).drain()
        assertEquals(0, first.settled)
        assertEquals(Delivery.Retry("ответ потерян"), storage.settled.single().second)
        assertEquals(SyncOperationStatus.PENDING, storage.operations.getValue(INTAKE).status)
        assertNotNull(first.retryAt)

        broken = false
        val second = worker(storage, transport).drain()
        assertEquals(0, second.settled) // задержка после первой попытки ещё не прошла
        val later = QueueWorker(storage, transport, resolver(true), Clock.fixed(now.plusSeconds(600), ZoneOffset.UTC))
        assertEquals(1, later.drain().settled)
        assertEquals(2, transport.sent.size)
        assertSame(transport.sent[0], transport.sent[1])
        assertEquals(1, storage.frozen)
    }

    @Test
    fun noConnectionStopsThePassAndKeepsTheOrder() = runTest {
        val storage = Storage(listOf(operation(sequence = 0), operation(id = OTHER_PACK, sequence = 1)))
        val transport = Transport { ApiResult.Failure(ApiFailure.Unavailable) }

        worker(storage, transport).drain()

        assertEquals(1, transport.sent.size)
    }

    @Test
    fun unreadableRowIsSkippedAndNamed() = runTest {
        val storage = Storage(emptyList())
        val broken = StoredSyncOperation.Unreadable(OTHER_PACK, StoredSyncOperation.Reason.Format("payload не разбирается"))
        storage.unreadable += broken
        val transport = Transport { ApiResult.Success(snapshotJson) }

        val report = worker(storage, transport).drain()

        assertEquals(listOf(broken), report.skipped)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun staleVocabularyIsReadOnceAndThePassRestarts() = runTest {
        val storage = Storage(emptyList())
        val miss = VocabularyMiss(VocabularyMiss.Kind.UNIT, MILLILITRES.id)
        storage.unreadable += StoredSyncOperation.Unreadable(OTHER_PACK, StoredSyncOperation.Reason.VocabularyStale(miss))
        val transport = Transport { ApiResult.Success(snapshotJson) }

        val report = worker(storage, transport, online = true).drain()

        assertEquals(1, store.refreshed)
        // Словарь дочитан, строка всё ещё не читается — второй проход её уже пропускает.
        assertEquals(1, report.skipped.size)
    }

    @Test
    fun lostPackageClosesAsAccessLost() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = Transport { ApiResult.Failure(ApiFailure.NotFound) }

        worker(storage, transport).drain()

        assertEquals(Delivery.AccessLost, storage.settled.single().second)
    }
}
