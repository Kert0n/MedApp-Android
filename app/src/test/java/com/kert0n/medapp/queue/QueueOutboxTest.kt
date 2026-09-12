package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.network.server.medAppHttpClient
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.network.value.VocabularyStore
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import io.ktor.client.engine.mock.MockEngine
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Outbox — единственный владелец прохода: он просыпается при старте, по сигналу таблицы и по
 * сроку повтора, сворачивает сигналы во время прохода в один и не даёт исключению прохода
 * уронить процесс (PLAN E4, F5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueOutboxTest {

    private val now: Instant = EARLIER.plusSeconds(3600)

    /** Хранилище, у которого считают чтения готовых и которое умеет сигналить и ломаться. */
    private class Storage : QueueStorage {
        val signals = MutableSharedFlow<Unit>()
        var reads = 0
        var broken = false
        var ready: List<StoredSyncOperation> = emptyList()
        val settled = mutableListOf<Settlement>()

        override fun changes(): Flow<Unit> = signals
        override suspend fun ready(now: Instant): List<StoredSyncOperation> {
            reads++
            if (broken) throw IllegalStateException("база недоступна")
            return ready
        }
        override suspend fun medKit(id: Uuid): MedKitRef? = null
        override suspend fun take(id: Uuid, fresh: PackageSnapshot?, at: Instant): Take? =
            ready.filterIsInstance<StoredSyncOperation.Readable>().firstOrNull { it.id == id }?.let { Take.Sending(it.operation) }
        override suspend fun answered(id: Uuid, answer: RawResponse, at: Instant) = Unit
        override suspend fun defer(id: Uuid, reason: String, at: Instant, notBefore: Instant) = Unit
        override suspend fun settle(id: Uuid, settlement: Settlement, at: Instant) {
            settled += settlement
            ready = emptyList()
        }
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
        override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation = error("не для этого теста")
    }

    private class Transport(private val answer: () -> ApiResult<RawResponse>) : QueueTransport {
        override suspend fun send(request: PreparedRequest): ApiResult<RawResponse> = answer()
        override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> =
            ApiResult.Failure(ApiFailure.Unavailable)
    }

    private class Store : VocabularyStore {
        override suspend fun snapshot() = Vocabulary(emptyList(), emptyList())
        override suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>) = Unit
    }

    private fun worker(storage: Storage, transport: Transport, clock: Clock): QueueWorker {
        val vocabulary = VocabularyResolver(
            Store(),
            MedAppApi(medAppHttpClient(MockEngine { throw java.io.IOException("связи нет") }, "https://medapp.test", retryDelay = { delayMillis(false) { 0L } }))
        )
        return QueueWorker(storage, transport, vocabulary, PackageSnapshotResolver(vocabulary, storage), clock)
    }

    /** Запрос, замороженный раньше: работник шлёт его как есть, чтения перед подготовкой нет. */
    private fun sendingOperation() = SyncOperation(
        id = INTAKE,
        command = PackageSyncCommand.Consume(PACK, dose("1"), INTAKE),
        sequence = 0,
        createdAt = EARLIER,
        payloadVersion = 1,
        prepared = PreparedRequest("PUT", "/v1/drugs/$PACK/sync/$INTAKE", body = "{}", preparedAt = EARLIER),
        status = SyncOperationStatus.SENDING
    )

    @Test
    fun startingThePassRunsOnceAndThenWaitsForTheTable() = runTest {
        val storage = Storage()
        val outbox = QueueOutbox(worker(storage, Transport { ApiResult.Failure(ApiFailure.Unavailable) }, Clock.fixed(now, ZoneOffset.UTC)), storage, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)

        outbox.start()
        runCurrent()

        assertEquals(1, storage.reads)
        assertEquals(1, outbox.state.value.passes)
        assertNull(outbox.state.value.lastFailure)
    }

    @Test
    fun aSignalFromTheTableStartsAPass() = runTest {
        val storage = Storage()
        val outbox = QueueOutbox(worker(storage, Transport { ApiResult.Failure(ApiFailure.Unavailable) }, Clock.fixed(now, ZoneOffset.UTC)), storage, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
        outbox.start()
        runCurrent()

        storage.signals.emit(Unit)
        runCurrent()

        assertEquals(2, outbox.state.value.passes)
    }

    /** Сигналы во время прохода — один следующий проход, а не по проходу на сигнал. */
    @Test
    fun signalsDuringAPassCollapseIntoOneMorePass() = runTest {
        val storage = Storage()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        storage.ready = listOf(StoredSyncOperation.Readable(sendingOperation()))
        val transport = Transport { ApiResult.Failure(ApiFailure.Unavailable) }
        val slow = object : QueueTransport by transport {
            override suspend fun send(request: PreparedRequest): ApiResult<RawResponse> {
                gate.await()
                return transport.send(request)
            }
        }
        val vocabulary = VocabularyResolver(Store(), MedAppApi(medAppHttpClient(MockEngine { throw java.io.IOException("связи нет") }, "https://medapp.test", retryDelay = { delayMillis(false) { 0L } })))
        val worker = QueueWorker(storage, slow, vocabulary, PackageSnapshotResolver(vocabulary, storage), Clock.fixed(now, ZoneOffset.UTC))
        val outbox = QueueOutbox(worker, storage, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
        outbox.start()
        runCurrent()
        assertEquals(0, outbox.state.value.passes)

        repeat(5) { storage.signals.emit(Unit) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        // Первый проход дочитал очередь (после исхода она пуста) и один следующий проход — по сигналам.
        assertEquals(2, outbox.state.value.passes)
    }

    /** Срок повтора из отчёта — таймер: проход приходит сам, без нового сигнала. */
    @Test
    fun theRetryTermFromTheReportWakesThePassOnItsOwn() = runTest {
        val storage = Storage()
        storage.ready = listOf(StoredSyncOperation.Readable(sendingOperation()))
        val outbox = QueueOutbox(worker(storage, Transport { ApiResult.Failure(ApiFailure.Unavailable) }, Clock.fixed(now, ZoneOffset.UTC)), storage, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
        outbox.start()
        runCurrent()
        val passes = outbox.state.value.passes
        assertNotNull(outbox.state.value.nextRunAt)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(passes, outbox.state.value.passes)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(passes + 1, outbox.state.value.passes)
    }

    /** База бросила мимо работника: процесс жив, сбой назван, очередь пробуется снова. */
    @Test
    fun aFailingPassDoesNotKillTheOutbox() = runTest {
        val storage = Storage()
        storage.broken = true
        val outbox = QueueOutbox(worker(storage, Transport { ApiResult.Failure(ApiFailure.Unavailable) }, Clock.fixed(now, ZoneOffset.UTC)), storage, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
        outbox.start()
        runCurrent()

        assertEquals(1, storage.reads)
        assertNotNull(outbox.state.value.lastFailure)

        storage.broken = false
        storage.signals.emit(Unit)
        runCurrent()

        assertEquals(2, outbox.state.value.passes)
        assertNull(outbox.state.value.lastFailure)
    }
}
