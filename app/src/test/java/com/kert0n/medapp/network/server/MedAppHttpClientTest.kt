package com.kert0n.medapp.network.server

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Автоповтор — свойство чтения, а не транспорта: GET после 5xx и обрыва повторяется, изменяющая
 * команда — никогда, потому что её исход мог примениться (PLAN B5, E3).
 */
class MedAppHttpClientTest {

    private val calls = AtomicInteger()

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = medAppHttpClient(
        MockEngine { request ->
            calls.incrementAndGet()
            handler(request)
        },
        "https://medapp.test",
        retryDelay = { delayMillis(false) { 0L } }
    )

    private fun answering(vararg statuses: HttpStatusCode) = client {
        respond("", statuses[minOf(calls.get() - 1, statuses.lastIndex)])
    }

    @Test
    fun readIsRetriedAfterServerError() = runTest {
        val response = answering(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK)
            .get("/v1/users/me")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, calls.get())
    }

    @Test
    fun readIsRetriedAfterBrokenConnection() = runTest {
        val response = client {
            if (calls.get() == 1) throw IOException("обрыв")
            respond("", HttpStatusCode.OK)
        }.get("/v1/users/me")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, calls.get())
    }

    @Test
    fun readGivesUpAfterBoundedRetries() = runTest {
        val response = answering(HttpStatusCode.BadGateway).get("/v1/med-kits")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(4, calls.get())
    }

    @Test
    fun mutatingCommandIsNeverRetriedByTransport() = runTest {
        val response = answering(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK)
            .post("/v1/drugs/00000000-0000-4000-8000-000000000011/intakes")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(1, calls.get())
    }

    @Test
    fun statusIsLeftToTheOperationNotThrown() = runTest {
        val response = answering(HttpStatusCode.NotFound).get("/v1/drugs/x")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(1, calls.get())
    }

    @Test
    fun requestsGoToTheConfiguredServer() = runTest {
        var host = ""
        client { request ->
            host = request.url.host
            respond("", HttpStatusCode.OK)
        }.get("/v1/form-types")

        assertEquals("medapp.test", host)
    }
}
