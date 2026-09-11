package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.value.Vocabulary
import org.junit.Assert.assertFalse
import java.math.BigDecimal
import com.kert0n.medapp.queue.pack.prepare
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.Claims
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

    /**
     * Очередь в памяти: операции, их запросы и исходы — ровно то, что видит работник. Состояние
     * пачки в «базе» — [known]: его переписывает свежий снимок при взятии и снимок из ответа при
     * закрытии, и по нему готовится запрос.
     */
    private class Storage(operations: List<SyncOperation>) : QueueStorage {
        val operations = operations.associateBy { it.id }.toMutableMap()
        val settled = mutableListOf<Pair<Uuid, Delivery>>()
        val unreadable = mutableListOf<StoredSyncOperation.Unreadable>()
        var frozen = 0
        var known = PackageSyncState(PACK, ResourceVersion(3))
        var knownPack: Package = pack(quantity = tablets("20"))
        val takenWith = mutableListOf<PackageSnapshotNetworkDTO?>()

        /** Снимок «лёг в базу»: версии, остаток и брони — те, что у сервера. */
        private fun learn(snapshot: PackageSnapshotNetworkDTO) {
            known = PackageSyncState(PACK, snapshot.pack.version, snapshot.claims.version)
            knownPack = pack(
                quantity = tablets(snapshot.pack.amount),
                claims = Claims(BigDecimal(snapshot.claims.total), snapshot.claims.mine?.let(::BigDecimal))
            )
        }

        override suspend fun ready(): List<StoredSyncOperation> =
            operations.values
                .filter { it.status == SyncOperationStatus.PENDING || it.status == SyncOperationStatus.SENDING }
                .sortedBy { it.sequence }
                .map<SyncOperation, StoredSyncOperation> { StoredSyncOperation.Readable(it) } + unreadable

        override suspend fun take(id: Uuid, fresh: PackageSnapshotNetworkDTO?, at: Instant): Take? {
            val operation = operations[id] ?: return null
            if (operation.status.isClosed) return null
            takenWith += fresh
            fresh?.let(::learn)
            val prepared = operation.prepared ?: run {
                frozen++
                when (val prepared = (operation.command as PackageSyncCommand).prepare(operation.id, knownPack, known, at)) {
                    is Preparation.Request -> prepared.request
                    is Preparation.Refuse -> return Take.Closed(Delivery.Refused(prepared.reason, PackageState.None)).also { settle(id, it.delivery, at) }
                    Preparation.AlreadyApplied -> return Take.Closed(Delivery.Applied(PackageState.None)).also { settle(id, it.delivery, at) }
                }
            }
            return Take.Sending(operation.with(status = SyncOperationStatus.SENDING, prepared = prepared).also { operations[id] = it })
        }

        override suspend fun <T> transaction(block: suspend () -> T): T = block()

        override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation =
            error("работник команд не ставит")

        override suspend fun settle(id: Uuid, outcome: Delivery, at: Instant) {
            settled += id to outcome
            val state = when (outcome) {
                is Delivery.Applied -> outcome.state
                is Delivery.Refused -> outcome.state
                is Delivery.Stale -> PackageState.Present(outcome.snapshot)
                is Delivery.Retry, Delivery.AccessLost -> PackageState.None
            }
            (state as? PackageState.Present)?.let { learn(it.snapshot) }
            val operation = operations.getValue(id)
            operations[id] = if (outcome is Delivery.Stale) {
                operation.with(status = SyncOperationStatus.PENDING, dropPrepared = true)
            } else {
                operation.with(
                    status = when (outcome) {
                        is Delivery.Applied -> SyncOperationStatus.APPLIED
                        is Delivery.Refused -> SyncOperationStatus.REFUSED
                        is Delivery.Retry -> SyncOperationStatus.PENDING
                        Delivery.AccessLost -> SyncOperationStatus.ACCESS_LOST
                        is Delivery.Stale -> error("разобрано выше")
                    },
                    attempts = operation.attempts + 1,
                    lastTriedAt = at
                )
            }
        }

        private fun SyncOperation.with(
            status: SyncOperationStatus = this.status,
            prepared: PreparedRequest? = this.prepared,
            attempts: Int = this.attempts,
            lastTriedAt: Instant? = this.lastTriedAt,
            dropPrepared: Boolean = false
        ) = SyncOperation(
            id, command, sequence, createdAt, payloadVersion, if (dropPrepared) null else prepared, groupId, dependsOn,
            status, attempts, lastError, lastTriedAt
        )
    }

    private class Transport(private val answer: (PreparedRequest) -> ApiResult<QueueAnswer>) : QueueTransport {
        val sent = mutableListOf<PreparedRequest>()
        val expected = mutableListOf<Expected>()
        var snapshots = 0
        var snapshotAnswer: ApiResult<PackageSnapshotNetworkDTO>? = null
        /** Снимок для чтения перед подготовкой; `null` — в этом тесте такого чтения не ждут. */
        var fresh: ApiResult<PackageSnapshotNetworkDTO>? = null

        override suspend fun send(request: PreparedRequest, expects: Expected): ApiResult<QueueAnswer> {
            sent += request
            expected += expects
            return answer(request)
        }

        /** Первое чтение — перед подготовкой ([fresh]); дальнейшие — истина после ответа ([snapshotAnswer]). */
        override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> {
            snapshots++
            val answer = if (snapshots == 1) fresh ?: snapshotAnswer else snapshotAnswer ?: fresh
            return requireNotNull(answer) { "снимок в этом тесте не ожидался" }
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
        lastTriedAt: Instant? = null,
        status: SyncOperationStatus = SyncOperationStatus.PENDING,
        prepared: PreparedRequest? = null
    ) = SyncOperation(
        id = id, command = command, sequence = sequence, createdAt = EARLIER, payloadVersion = 1,
        prepared = prepared, status = status, attempts = attempts, lastTriedAt = lastTriedAt
    )

    /** Снимок с другой версией пачки: то, что сервер знает сейчас, а устройство — ещё нет. */
    private fun snapshotWithVersion(version: Long): PackageSnapshotNetworkDTO =
        medAppJson.decodeFromString(
            PackageSnapshotNetworkDTO.serializer(),
            snapshotJson.replace("\"version\":4", "\"version\":$version")
        )

    /** Транспорт, у которого чтение перед подготовкой отвечает снимком [fresh]. */
    private fun transport(fresh: PackageSnapshotNetworkDTO = snapshot, answer: (PreparedRequest) -> ApiResult<QueueAnswer>) =
        Transport(answer).also { it.fresh = ApiResult.Success(fresh) }

    private fun worker(storage: Storage, transport: Transport, online: Boolean = true) =
        QueueWorker(storage, transport, resolver(online), clock)

    @Test
    fun pendingOperationIsSentAndSettledDoneWithTheSnapshot() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        // Внеплановый расход — тоже `sync` под своим номером: у него есть номер, и повтор
        // под ним сервер применит один раз (решение владельца, PLAN B4).
        assertEquals("PUT", transport.sent.single().method)
        assertEquals("/v1/drugs/$PACK/sync/$INTAKE", transport.sent.single().path)
        assertFalse(transport.sent.single().body!!.contains("reservation"))
        assertEquals(Delivery.Applied(PackageState.Present(snapshot)), storage.settled.single().second)
        assertEquals(Expected.SNAPSHOT_OR_GONE, transport.expected.single())
        assertEquals(SyncOperationStatus.APPLIED, storage.operations.getValue(INTAKE).status)
        assertNull(report.retryAt)
    }

    /** Предусловие — то, что у сервера сейчас, а не то, что устройство видело когда-то (PLAN E2, E3). */
    @Test
    fun requestIsPreparedFromTheStateJustReadNotFromTheStoredRow() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = transport(fresh = snapshotWithVersion(7)) { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        worker(storage, transport).drain()

        assertEquals(1, transport.snapshots)
        assertEquals(ResourceVersion(7), transport.sent.single().drugVersion)
        assertTrue(transport.sent.single().body!!.contains("\"drugVersion\":7"))
        assertEquals(snapshotWithVersion(7), storage.takenWith.single())
    }

    /** Ответ на первую операцию пачки уже лёг в базу — вторая готовится по нему, без второго чтения. */
    @Test
    fun theNextOperationOfThePackageIsPreparedFromTheAnswerOfThePrevious() = runTest {
        val second = PackageSyncCommand.Consume(PACK, dose("1"), OTHER_PACK)
        val storage = Storage(listOf(operation(sequence = 0), operation(second, id = OTHER_PACK, sequence = 1)))
        val transport = transport(fresh = snapshotWithVersion(7)) { ApiResult.Success(QueueAnswer.Snapshot(snapshotWithVersion(8))) }

        val report = worker(storage, transport).drain()

        assertEquals(2, report.settled)
        assertEquals(1, transport.snapshots)
        assertEquals(listOf(ResourceVersion(7), ResourceVersion(8)), transport.sent.map { it.drugVersion })
        assertEquals(listOf(snapshotWithVersion(7), null), storage.takenWith)
    }

    /** Отправка, пережившая смерть процесса: исход неизвестен, запрос уже заморожен — уходит как есть. */
    @Test
    fun aSendingSurvivorGoesOutAsItWasWithoutReadingFirst() = runTest {
        val frozen = consume.toPreparedRequest(INTAKE, PackageSyncState(PACK, ResourceVersion(3)), tablets("20"), null, EARLIER)
        val storage = Storage(listOf(operation(status = SyncOperationStatus.SENDING, prepared = frozen)))
        val transport = Transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        worker(storage, transport).drain()

        assertEquals(0, transport.snapshots)
        assertSame(frozen, transport.sent.single())
        assertEquals(SyncOperationStatus.APPLIED, storage.operations.getValue(INTAKE).status)
    }

    /** Пачки на сервере нет уже при чтении: доступа к ней нет, и отправлять нечего. */
    @Test
    fun packageGoneBeforeTheReadClosesAsAccessLostWithoutSending() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = Transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }
        transport.fresh = ApiResult.Failure(ApiFailure.NotFound)

        worker(storage, transport).drain()

        assertTrue(transport.sent.isEmpty())
        assertEquals(Delivery.AccessLost, storage.settled.single().second)
    }

    /**
     * 409 у `sync` — версия устарела, запрос отвергнут до применения (PLAN B3, E3): состояние
     * читается и ложится в базу, запрос готовится заново под тем же номером и уходит тем же
     * проходом — со свежей версией.
     */
    @Test
    fun staleSyncIsRepreparedFromTheFreshStateUnderTheSameNumber() = runTest {
        val storage = Storage(listOf(operation(sync)))
        var attempts = 0
        val transport = transport(fresh = snapshotWithVersion(3)) {
            attempts++
            if (attempts == 1) ApiResult.Failure(ApiFailure.Conflict) else ApiResult.Success(QueueAnswer.Snapshot(snapshotWithVersion(8)))
        }
        transport.snapshotAnswer = ApiResult.Success(snapshotWithVersion(7))

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        assertEquals(Delivery.Stale(snapshotWithVersion(7)), storage.settled[0].second)
        assertEquals(Delivery.Applied(PackageState.Present(snapshotWithVersion(8))), storage.settled[1].second)
        assertEquals(2, transport.sent.size)
        assertTrue(transport.sent.all { it.path.endsWith("/sync/$INTAKE") })
        assertEquals(listOf(ResourceVersion(3), ResourceVersion(7)), transport.sent.map { it.drugVersion })
        // Тело то же — меняется только версия: иначе журнал ответил бы 409 навсегда.
        assertEquals(
            transport.sent[0].body!!.replace("\"drugVersion\":3", "\"drugVersion\":7"),
            transport.sent[1].body
        )
        assertEquals(SyncOperationStatus.APPLIED, storage.operations.getValue(INTAKE).status)
    }

    /** Потерянный ответ, за которым пришёл 409: своя бронь уже равна заявленной — расход применён. */
    @Test
    fun staleSyncWhoseClaimAlreadyMatchesIsAppliedWithoutResending() = runTest {
        val frozen = sync.toPreparedRequest(INTAKE, PackageSyncState(PACK, ResourceVersion(3), ResourceVersion(1)), tablets("20"), tablets("7"), EARLIER)
        val storage = Storage(listOf(operation(sync, status = SyncOperationStatus.SENDING, prepared = frozen)))
        val transport = Transport { ApiResult.Failure(ApiFailure.Conflict) }
        transport.snapshotAnswer = ApiResult.Success(snapshot) // mine = 4 = claimAfter, было 7

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        assertEquals(1, transport.sent.size)
        assertEquals(Delivery.Applied(PackageState.Present(snapshot)), storage.settled.single().second)
    }

    /** Чужая правка перекрыла описание: отказ с названной причиной, истина прочитана, человек смотрит заново. */
    @Test
    fun staleDescriptionIsRefusedWithTheReasonNamedAndTheTruthRead() = runTest {
        val describe = PackageSyncCommand.Describe(
            PACK,
            com.kert0n.medapp.domain.pack.PackageSharedFacts("Парацетамол", TABLET_FORM),
            com.kert0n.medapp.domain.pack.PackageSharedFacts("Парацетамол 500", TABLET_FORM)
        )
        val storage = Storage(listOf(operation(describe)))
        val transport = transport { ApiResult.Failure(ApiFailure.PreconditionFailed) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        worker(storage, transport).drain()

        assertEquals(Delivery.Refused(RefusalReason.STALE, PackageState.Present(snapshot)), storage.settled.single().second)
        assertEquals(SyncOperationStatus.REFUSED, storage.operations.getValue(INTAKE).status)
    }

    @Test
    fun invalidInputIsRefusedAndTheTruthRead() = runTest {
        val describe = PackageSyncCommand.Describe(
            PACK,
            com.kert0n.medapp.domain.pack.PackageSharedFacts("Парацетамол", TABLET_FORM),
            com.kert0n.medapp.domain.pack.PackageSharedFacts("Парацетамол 500", TABLET_FORM)
        )
        val storage = Storage(listOf(operation(describe)))
        val transport = transport { ApiResult.Failure(ApiFailure.Invalid(emptyList())) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        worker(storage, transport).drain()

        assertEquals(Delivery.Refused(RefusalReason.INVALID, PackageState.Present(snapshot)), storage.settled.single().second)
    }

    @Test
    fun retryAfterIsHonouredAndNothingElseIsSentInThatPass() = runTest {
        val storage = Storage(listOf(operation(sequence = 0), operation(id = OTHER_PACK, sequence = 1)))
        val transport = transport { ApiResult.Failure(ApiFailure.TooManyRequests(30.seconds)) }

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
        val transport = transport { if (broken) ApiResult.Failure(ApiFailure.OutcomeUnknown) else ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

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
        val transport = transport { ApiResult.Failure(ApiFailure.Unavailable) }

        worker(storage, transport).drain()

        assertEquals(1, transport.sent.size)
    }

    @Test
    fun unreadableRowIsSkippedAndNamed() = runTest {
        val storage = Storage(emptyList())
        val broken = StoredSyncOperation.Unreadable(OTHER_PACK, StoredSyncOperation.Reason.Format("payload не разбирается"))
        storage.unreadable += broken
        val transport = transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        val report = worker(storage, transport).drain()

        assertEquals(listOf(broken), report.skipped)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun staleVocabularyIsReadOnceAndThePassRestarts() = runTest {
        val storage = Storage(emptyList())
        val miss = VocabularyMiss(VocabularyMiss.Kind.UNIT, MILLILITRES.id)
        storage.unreadable += StoredSyncOperation.Unreadable(OTHER_PACK, StoredSyncOperation.Reason.VocabularyStale(miss))
        val transport = transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        val report = worker(storage, transport, online = true).drain()

        assertEquals(1, store.refreshed)
        // Словарь дочитан, строка всё ещё не читается — второй проход её уже пропускает.
        assertEquals(1, report.skipped.size)
    }

    /** Ответ не по форме — сбой протокола, а не «пустая пачка» и не исключение: повтор тем же запросом. */
    @Test
    fun answerOutOfShapeIsRetriedAndNothingIsApplied() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = transport { ApiResult.Failure(ApiFailure.Protocol("пустое тело там, где контракт обещает снимок")) }

        val report = worker(storage, transport).drain()

        assertEquals(0, report.settled)
        assertEquals(Delivery.Retry("пустое тело там, где контракт обещает снимок"), storage.settled.single().second)
        assertEquals(SyncOperationStatus.PENDING, storage.operations.getValue(INTAKE).status)
    }

    /** 404 у снятия брони — брони уже нет: желаемое наступило, а пачка на месте и доступ не потерян. */
    @Test
    fun missingClaimOnReleaseIsAppliedAndDoesNotMarkThePackage() = runTest {
        val storage = Storage(listOf(operation(PackageSyncCommand.ReleaseClaim(PACK))))
        val transport = transport { ApiResult.Failure(ApiFailure.NotFound) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        worker(storage, transport).drain()

        assertEquals(Delivery.Applied(PackageState.Present(snapshot)), storage.settled.single().second)
    }

    /** 409 на заявлении брони — она уже есть: по свежему `mine` та же команда становится правкой. */
    @Test
    fun claimAlreadyDeclaredIsRepreparedAsAPatch() = runTest {
        val storage = Storage(listOf(operation(PackageSyncCommand.SetClaim(PACK, tablets("6")))))
        val noClaim = medAppJson.decodeFromString(
            PackageSnapshotNetworkDTO.serializer(), snapshotJson.replace(",\"mine\":\"4.000000\"", "")
        )
        var attempts = 0
        val transport = transport(fresh = noClaim) {
            attempts++
            if (attempts == 1) ApiResult.Failure(ApiFailure.Conflict)
            else ApiResult.Success(QueueAnswer.Claim(com.kert0n.medapp.network.pack.ClaimNetworkDTO(PACK, "6")))
        }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        val report = worker(storage, transport).drain()

        assertEquals(1, report.settled)
        assertEquals(listOf("POST", "PATCH"), transport.sent.map { it.method })
        assertEquals(SyncOperationStatus.APPLIED, storage.operations.getValue(INTAKE).status)
    }

    /** 400 на создании — пачка не заведена, читать нечего и терять доступ не к чему. */
    @Test
    fun invalidCreateIsRefusedWithoutTouchingThePackage() = runTest {
        val create = PackageSyncCommand.Create(PACK, HOME_KIT, tablets("20"), com.kert0n.medapp.domain.pack.PackageSharedFacts("Парацетамол", TABLET_FORM))
        val storage = Storage(listOf(operation(create)))
        val transport = Transport { ApiResult.Failure(ApiFailure.Invalid(emptyList())) }

        worker(storage, transport).drain()

        assertEquals(0, transport.snapshots)
        assertEquals(Delivery.Refused(RefusalReason.INVALID, PackageState.None), storage.settled.single().second)
    }

    /** Расход больше остатка — отказ по количеству, пачка остаётся какой её знает сервер: удалять её нечем. */
    @Test
    fun consumeBeyondTheStockIsRefusedAsInsufficientAndThePackageStays() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = transport { ApiResult.Failure(ApiFailure.Invalid(emptyList())) }
        transport.snapshotAnswer = ApiResult.Success(snapshot)

        worker(storage, transport).drain()

        assertEquals(Delivery.Refused(RefusalReason.INSUFFICIENT, PackageState.Present(snapshot)), storage.settled.single().second)
    }

    /** Единицу пачки сменили: дозу в прежней единице на провод не везут — единицы там нет. */
    @Test
    fun consumeInAUnitThePackageNoLongerUsesIsRefusedBeforeSending() = runTest {
        val inMillilitres = PackageSyncCommand.Consume(PACK, dose(com.kert0n.medapp.fixture.millilitres("5")), INTAKE)
        val storage = Storage(listOf(operation(inMillilitres)))
        val transport = transport { ApiResult.Success(QueueAnswer.Snapshot(snapshot)) }

        val report = worker(storage, transport).drain()

        assertTrue(transport.sent.isEmpty())
        assertEquals(1, report.settled)
        assertEquals(Delivery.Refused(RefusalReason.UNIT_CHANGED, PackageState.None), storage.settled.single().second)
    }

    @Test
    fun deletingWhatIsAlreadyGoneIsApplied() = runTest {
        val storage = Storage(listOf(operation(PackageSyncCommand.Delete(PACK))))
        val transport = transport { ApiResult.Failure(ApiFailure.NotFound) }

        worker(storage, transport).drain()

        assertEquals(Delivery.Applied(PackageState.Gone), storage.settled.single().second)
    }

    @Test
    fun lostPackageClosesAsAccessLost() = runTest {
        val storage = Storage(listOf(operation()))
        val transport = transport { ApiResult.Failure(ApiFailure.NotFound) }

        worker(storage, transport).drain()

        assertEquals(Delivery.AccessLost, storage.settled.single().second)
    }
}
