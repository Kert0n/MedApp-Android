package com.kert0n.medapp.queue.medkit

import com.kert0n.medapp.domain.Unavailability
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLETS_ID
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.network.medkit.MedKitPublication
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.network.server.medAppHttpClient
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.network.value.VocabularyStore
import com.kert0n.medapp.queue.PackageSnapshotResolver
import com.kert0n.medapp.queue.QueueStorage
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Settlement
import com.kert0n.medapp.queue.Transactions
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Публикация — два чтения-записи вокруг сети (PLAN E5): что уехало, что легло, и как называется
 * то, что не вышло. Саму сетевую сторону — доводку по идентификаторам и откат — проверяет
 * `MedKitPublicationTest`; здесь — владелец обеих транзакций.
 */
class MedKitPublishingTest {

    private val now = Instant.parse("2026-09-12T12:00:00Z")

    private fun drug(id: Uuid) = """
        {"drug":{"id":"$id","name":"Парацетамол","quantity":"20.000000","quantityUnitId":"$TABLETS_ID",
         "medKitId":"$HOME_KIT","version":1},"reservations":{"total":"0.000000","version":1}}
    """

    /** Хранилище публикации в памяти: аптечка, её пачки и то, что записали переключением. */
    private class Storage(
        var medKit: MedKit?,
        var contents: List<Package>,
        private val switch: PublicationStorage.Switch = PublicationStorage.Switch.PUBLISHED
    ) : PublicationStorage {
        var published: Pair<Uuid, List<PackageSnapshot>>? = null
        override suspend fun medKit(id: Uuid): MedKit? = medKit?.takeIf { it.id == id }
        override suspend fun contentsOf(medKitId: Uuid): List<Package> = contents
        override suspend fun publish(
            medKitId: Uuid,
            snapshots: List<PackageSnapshot>,
            at: Instant
        ): PublicationStorage.Switch {
            if (switch != PublicationStorage.Switch.PUBLISHED) return switch
            published = medKitId to snapshots
            return switch
        }
    }

    private class Transaction : Transactions {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private class Words : VocabularyStore {
        override suspend fun snapshot() = Vocabulary(listOf(TABLETS), listOf(TABLET_FORM))
        override suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>) = Unit
    }

    /** Очередь здесь нужна только затем, чтобы назвать аптечку снимка. */
    private class Queue : QueueStorage {
        override suspend fun medKit(id: Uuid): MedKitRef? =
            if (id == HOME_KIT) com.kert0n.medapp.fixture.medKit(id = HOME_KIT).ref else null
        override fun changes(): Flow<Unit> = emptyFlow()
        override suspend fun nextDueAt(now: Instant): Instant? = null
        override suspend fun ready(now: Instant) = error("не для этого теста")
        override suspend fun take(id: Uuid, fresh: PackageSnapshot?, at: Instant) = error("не для этого теста")
        override suspend fun answered(id: Uuid, answer: RawResponse, at: Instant) = error("не для этого теста")
        override suspend fun defer(id: Uuid, reason: String, at: Instant, notBefore: Instant) = error("не для этого теста")
        override suspend fun settle(id: Uuid, settlement: Settlement, at: Instant) = error("не для этого теста")
        override suspend fun enqueue(queued: QueuedCommand, at: Instant) = error("не для этого теста")
    }

    private fun publishing(storage: Storage, answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>): MedKitPublishing {
        val api = MedAppApi(
            medAppHttpClient(
                MockEngine { request ->
                    val (status, body) = answer(request)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
                "https://medapp.test",
                retryDelay = { delayMillis(false) { 0L } }
            )
        )
        return MedKitPublishing(
            storage = storage,
            publication = MedKitPublication(api),
            resolver = PackageSnapshotResolver(VocabularyResolver(Words(), api), Queue()),
            transactions = Transaction(),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    private val happyServer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String> = { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/med-kits") -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
            path.endsWith("/drugs") -> {
                val sent = String(request.body.toByteArray())
                HttpStatusCode.Created to drug(Uuid.parse(sent.substringAfter("\"id\":\"").substringBefore("\"")))
            }
            else -> HttpStatusCode.OK to "[]"
        }
    }

    @Test
    fun publishedMedKitIsSwitchedWithTheFirstConfirmedAmounts() = runTest {
        val storage = Storage(medKit(), listOf(pack(id = PACK), pack(id = OTHER_PACK)))

        val outcome = publishing(storage, happyServer).publish(HOME_KIT)

        assertEquals(MedKitPublishing.Outcome.Published, outcome)
        val (switched, snapshots) = requireNotNull(storage.published)
        assertEquals(HOME_KIT, switched)
        assertEquals(setOf(PACK, OTHER_PACK), snapshots.mapTo(HashSet()) { it.pack.id })
        assertEquals(1L, snapshots.first().sync.version?.number)
    }

    @Test
    fun aMedKitThatChangedMeanwhileIsNotSwitched() = runTest {
        val storage = Storage(medKit(), listOf(pack(id = PACK)), PublicationStorage.Switch.CHANGED_MEANWHILE)

        assertEquals(MedKitPublishing.Outcome.ChangedMeanwhile, publishing(storage, happyServer).publish(HOME_KIT))
        assertNull(storage.published)
    }

    /**
     * Аптечку убрали, пока шла сеть: переключение отвечает «её нет», и сценарий говорит то же.
     * Вставкой она не воскресает — сказать об этом честнее, чем завести её заново (PLAN E5).
     */
    @Test
    fun aMedKitRemovedWhileTheNetworkWasBusyIsReportedGone() = runTest {
        val storage = Storage(medKit(), listOf(pack(id = PACK)), PublicationStorage.Switch.MED_KIT_GONE)

        assertEquals(MedKitPublishing.Outcome.MedKitGone, publishing(storage, happyServer).publish(HOME_KIT))
        assertNull(storage.published)
    }

    /** Обрыв на записи — исход неизвестен: сервер промолчал, начатое не откатывается, повтор доведёт (E5). */
    @Test
    fun aLostAnswerIsNamedAndNothingIsSwitched() = runTest {
        val storage = Storage(medKit(), listOf(pack(id = PACK)))

        val outcome = publishing(storage) { throw IOException("нет связи") }.publish(HOME_KIT)

        assertEquals(MedKitPublishing.Outcome.Refused(Unavailability.SERVER_SILENT, rolledBack = false), outcome)
        assertNull(storage.published)
    }

    @Test
    fun anAlreadyPublishedOrMissingMedKitIsNotPublishedAgain() = runTest {
        val shared = Storage(medKit(publication = MedKit.Publication.PUBLISHED), emptyList())
        assertEquals(MedKitPublishing.Outcome.AlreadyPublished, publishing(shared, happyServer).publish(HOME_KIT))
        val gone = Storage(null, emptyList())
        assertEquals(MedKitPublishing.Outcome.MedKitGone, publishing(gone, happyServer).publish(HOME_KIT))
    }
}
