package com.kert0n.medapp.network.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
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

/** Аптечка на сервере появляется целиком или не появляется вовсе (PLAN E5). */
class MedKitPublicationTest {

    private val requests = mutableListOf<String>()

    private fun drug(id: String) = """
        {"drug":{"id":"$id","name":"Парацетамол","quantity":"20.000000","quantityUnitId":"$TABLETS",
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
        assertTrue(outcome is MedKitPublication.Outcome.Published)
        outcome as MedKitPublication.Outcome.Published
        assertEquals(MedKit.Publication.PUBLISHED, outcome.medKit.publication)
        assertEquals(2, outcome.packages.size)
        assertEquals(
            listOf("POST /v1/med-kits", "POST /v1/med-kits/$HOME_KIT/drugs", "POST /v1/med-kits/$HOME_KIT/drugs"),
            requests
        )
    }

    @Test
    fun aBreakInTheMiddleDeletesWhatWasAlreadyCreated() = runTest {
        // Первая пачка уехала, вторая оборвалась: аптечка с одной пачкой на сервере — это
        // половина, которой не бывает, поэтому она удаляется целиком.
        var drugs = 0
        val service = publication { request ->
            when {
                request.method.value == "DELETE" -> HttpStatusCode.NoContent to ""
                request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
                request.url.encodedPath.endsWith("/drugs") ->
                    if (drugs++ == 0) HttpStatusCode.Created to drug(PACK.toString()) else throw IOException("обрыв")
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val outcome = service.publish(local, packages)
        assertEquals(MedKitPublication.Outcome.Refused(ApiFailure.OutcomeUnknown, rolledBack = true), outcome)
        assertEquals("DELETE /v1/med-kits/$HOME_KIT", requests.last())
    }

    @Test
    fun refusedKitCreationHasNothingToRollBack() = runTest {
        val service = publication { HttpStatusCode.Conflict to "" }
        val outcome = service.publish(local, packages)
        assertEquals(MedKitPublication.Outcome.Refused(ApiFailure.Conflict, rolledBack = true), outcome)
        assertEquals(listOf("POST /v1/med-kits"), requests)
    }

    @Test
    fun failedRollbackIsReportedNotHidden() = runTest {
        val service = publication { request ->
            when {
                request.method.value == "DELETE" -> throw IOException("связи нет")
                request.url.encodedPath == "/v1/med-kits" -> HttpStatusCode.Created to """{"id":"$HOME_KIT"}"""
                else -> throw IOException("связи нет")
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
