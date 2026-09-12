package com.kert0n.medapp.network.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS_ID
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.medAppHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Публикация продолжается по идентификаторам: у серверной копии до переключения один писатель (PLAN E5). */
class MedKitPublicationTest {

    private val requests = mutableListOf<String>()

    private fun drug(id: String) = """
        {"drug":{"id":"$id","name":"Парацетамол","quantity":"20.000000","quantityUnitId":"$TABLETS_ID",
         "medKitId":"$HOME_KIT","version":1},"reservations":{"total":"0.000000","version":1}}
    """

    private fun publication(answer: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        MedKitPublication(
            MedAppApi(
                medAppHttpClient(
                    MockEngine { request ->
                        requests += "${request.method.value} ${request.url.encodedPath}"
                        val (status, body) = answer(request)
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                    "https://medapp.test",
                    retryDelay = { delayMillis(false) { 0L } }
                )
            )
        )

    private val local = medKit(publication = MedKit.Publication.LOCAL)

    private val packages = listOf(pack(id = PACK), pack(id = OTHER_PACK))

    @Test
    fun wholeKitGoesUpAndComesBackPublished() = runTest {
        val service = publication { request ->
            when {
                request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
                request.url.encodedPath.endsWith("/drugs") -> HttpStatusCode.Created to drug(PACK.toString())
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val outcome = service.publish(local, packages)
        assertTrue("$outcome", outcome is MedKitPublication.Outcome.Published)
        outcome as MedKitPublication.Outcome.Published
        assertEquals(2, outcome.packages.size)
        assertEquals(
            listOf("POST /v1/med-kits", "POST /v1/med-kits/$HOME_KIT/drugs", "POST /v1/med-kits/$HOME_KIT/drugs"),
            requests
        )
    }

    /**
     * Первая пачка уехала, вторая оборвалась: до переключения у серверной копии один писатель —
     * мы, поэтому половина не удаляется, а доводится следующей попыткой по идентификаторам.
     */
    @Test
    fun aBreakInTheMiddleIsResumedNotRolledBack() = runTest {
        var drugs = 0
        var kitCreated = false
        var connected = false
        val service = publication { request ->
            when {
                request.method.value == "DELETE" -> error("откат на обрыве не нужен")
                request.method.value == "POST" && request.url.encodedPath == "/v1/med-kits" ->
                    if (kitCreated) HttpStatusCode.Conflict to "" else { kitCreated = true; HttpStatusCode.Created to """{"id":"$HOME_KIT"}""" }
                request.method.value == "GET" && request.url.encodedPath == "/v1/med-kits/$HOME_KIT" ->
                    HttpStatusCode.OK to """{"id":"$HOME_KIT","userCount":1,"drugs":[${drug(PACK.toString())}]}"""
                request.url.encodedPath.endsWith("/drugs") -> when {
                    drugs++ == 0 -> HttpStatusCode.Created to drug(PACK.toString())
                    connected -> HttpStatusCode.Created to drug(OTHER_PACK.toString())
                    else -> throw IOException("обрыв")
                }
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val broken = service.publish(local, packages)
        assertEquals(MedKitPublication.Outcome.Refused(ApiFailure.OutcomeUnknown, rolledBack = false), broken)
        // Исход второй пачки неизвестен — её читают: 404, значит не дошла.
        assertEquals("GET /v1/drugs/$OTHER_PACK", requests.last())
        val firstAttempt = requests.size

        // Повтор: аптечка уже есть — читаем; первая пачка уже есть и совпадает — не трогаем;
        // вторую досоздаём. Ни одного удаления.
        connected = true
        val resumed = service.publish(local, packages)
        assertTrue("$resumed", resumed is MedKitPublication.Outcome.Published)
        assertEquals(2, (resumed as MedKitPublication.Outcome.Published).packages.size)
        assertEquals(
            listOf("POST /v1/med-kits", "GET /v1/med-kits/$HOME_KIT", "POST /v1/med-kits/$HOME_KIT/drugs"),
            requests.drop(firstAttempt)
        )
    }

    /** Между попытками человек принял таблетку: серверная пачка прошлой попытки правится до местной. */
    @Test
    fun aPackageChangedBetweenAttemptsIsPatchedToTheLocalState() = runTest {
        val service = publication { request ->
            when {
                request.method.value == "POST" && request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Conflict to ""
                request.method.value == "GET" && request.url.encodedPath == "/v1/med-kits/$HOME_KIT" ->
                    HttpStatusCode.OK to """{"id":"$HOME_KIT","userCount":1,"drugs":[${drug(PACK.toString())}]}"""
                request.method.value == "PATCH" -> HttpStatusCode.OK to drug(PACK.toString()).replace("20.000000", "18.000000")
                request.url.encodedPath.endsWith("/drugs") -> HttpStatusCode.Created to drug(OTHER_PACK.toString())
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val eighteen = listOf(pack(id = PACK, quantity = com.kert0n.medapp.fixture.tablets("18")), pack(id = OTHER_PACK))

        val outcome = service.publish(local, eighteen)

        assertTrue("$outcome", outcome is MedKitPublication.Outcome.Published)
        assertEquals("PATCH /v1/drugs/$PACK", requests[2])
        assertEquals("18.000000", (outcome as MedKitPublication.Outcome.Published).packages.first().pack.amount)
    }

    /** Местно пачку уже выбросили, а серверу она досталась прошлой попыткой: удаляется. */
    @Test
    fun aPackageGoneLocallyIsDeletedOnTheServer() = runTest {
        val service = publication { request ->
            when {
                request.method.value == "POST" && request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Conflict to ""
                request.method.value == "GET" ->
                    HttpStatusCode.OK to """{"id":"$HOME_KIT","userCount":1,"drugs":[${drug(PACK.toString())},${drug(OTHER_PACK.toString())}]}"""
                request.method.value == "DELETE" -> HttpStatusCode.NoContent to ""
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val outcome = service.publish(local, listOf(pack(id = PACK)))
        assertTrue("$outcome", outcome is MedKitPublication.Outcome.Published)
        assertEquals("DELETE /v1/drugs/$OTHER_PACK", requests.last())
    }

    @Test
    fun refusedKitCreationHasNothingToRollBack() = runTest {
        val service = publication { HttpStatusCode.BadRequest to "" }
        val outcome = service.publish(local, packages)
        assertEquals(MedKitPublication.Outcome.Refused(ApiFailure.Invalid(emptyList()), rolledBack = true), outcome)
        assertEquals(listOf("POST /v1/med-kits"), requests)
    }

    /** Отказ на пачке откатывает аптечку целиком: после отказа на сервере либо всё, либо ничего. */
    @Test
    fun aRefusedPackageRollsTheKitBack() = runTest {
        val service = publication { request ->
            when {
                request.method.value == "DELETE" -> HttpStatusCode.NoContent to ""
                request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
                else -> HttpStatusCode.BadRequest to ""
            }
        }
        val outcome = service.publish(local, packages)
        assertEquals(MedKitPublication.Outcome.Refused(ApiFailure.Invalid(emptyList()), rolledBack = true), outcome)
        assertEquals("DELETE /v1/med-kits/$HOME_KIT", requests.last())
    }

    @Test
    fun failedRollbackIsReportedNotHidden() = runTest {
        val service = publication { request ->
            when {
                request.method.value == "DELETE" -> throw IOException("связи нет")
                request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
                else -> HttpStatusCode.BadRequest to ""
            }
        }
        val outcome = service.publish(local, packages) as MedKitPublication.Outcome.Refused
        assertEquals(false, outcome.rolledBack)
    }

    @Test(expected = IllegalStateException::class)
    fun publishedKitIsNotPublishedAgain() = runTest {
        publication { HttpStatusCode.NotFound to "" }
            .publish(medKit(publication = MedKit.Publication.PUBLISHED), emptyList())
    }
}
