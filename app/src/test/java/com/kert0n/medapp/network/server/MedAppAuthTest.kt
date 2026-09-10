package com.kert0n.medapp.network.server

import com.kert0n.medapp.network.account.AccessTokenUnavailable
import com.kert0n.medapp.network.account.AccessTokens
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.CredentialSource
import com.kert0n.medapp.network.account.StoredAccount
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пропуск выдаётся по учётке и перевыпускается ровно один раз на 401; параллельные 401 делят
 * одну выдачу (PLAN B1, B5).
 */
class MedAppAuthTest {

    private val account = AccountCredentials(
        login = Uuid.parse("00000000-0000-4000-8000-000000000071"),
        key = "k3y"
    )

    private class Stored(var account: StoredAccount) : CredentialSource {
        override suspend fun read(): StoredAccount = account
        override suspend fun save(credentials: AccountCredentials) {
            account = StoredAccount.Present(credentials)
        }
    }

    private val tokenCalls = AtomicInteger()
    private val resourceCalls = AtomicInteger()
    private val seenAuthorization = mutableListOf<String?>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Сервер выдаёт пропуски `t1`, `t2`, … и принимает ресурс только с тем, что назовёт [accepts]. */
    private fun client(
        stored: StoredAccount = StoredAccount.Present(account),
        tokenStatus: HttpStatusCode = HttpStatusCode.OK,
        accepts: (String?) -> Boolean = { it == "Bearer t${tokenCalls.get()}" }
    ) = medAppHttpClient(
        MockEngine { request -> answer(request, tokenStatus, accepts) },
        "https://medapp.test",
        tokens = AccessTokens(Stored(stored)),
        retryDelay = { delayMillis(false) { 0L } }
    )

    private fun MockRequestHandleScope.answer(
        request: HttpRequestData,
        tokenStatus: HttpStatusCode,
        accepts: (String?) -> Boolean
    ): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        if (request.url.encodedPath == "/v1/auth/token") {
            val number = tokenCalls.incrementAndGet()
            val basic = "Basic " + Base64.getEncoder()
                .encodeToString("${account.login}:${account.key}".toByteArray())
            assertEquals(basic, authorization)
            return if (tokenStatus == HttpStatusCode.OK) {
                respond("""{"accessToken":"t$number"}""", HttpStatusCode.OK, json)
            } else {
                respond("", tokenStatus)
            }
        }
        resourceCalls.incrementAndGet()
        synchronized(seenAuthorization) { seenAuthorization += authorization }
        return respond("", if (accepts(authorization)) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
    }

    @Test
    fun firstRequestTakesATokenByBasicAndSendsItAsBearer() = runTest {
        val response = client().get("/v1/users/me")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, tokenCalls.get())
        assertEquals(listOf<String?>("Bearer t1"), seenAuthorization)
    }

    @Test
    fun tokenLivesInMemoryAndIsReused() = runTest {
        val client = client()
        client.get("/v1/users/me")
        client.get("/v1/med-kits")

        assertEquals(1, tokenCalls.get())
    }

    /** 401 значит, что команда не принята к исполнению, поэтому повтор безопасен и для расхода. */
    @Test
    fun expiredTokenIsReissuedOnceAndTheCommandRepeated() = runTest {
        var accepted = "Bearer t1"
        val client = client(accepts = { it == accepted })
        client.get("/v1/users/me")
        accepted = "Bearer t2"

        val response = client.post("/v1/drugs/00000000-0000-4000-8000-000000000011/intakes")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, tokenCalls.get())
        assertEquals(listOf<String?>("Bearer t1", "Bearer t1", "Bearer t2"), seenAuthorization)
    }

    @Test
    fun secondUnauthorizedIsReturnedNotLooped() = runTest {
        val response = client(accepts = { false }).get("/v1/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(2, tokenCalls.get())
        assertEquals(2, resourceCalls.get())
    }

    @Test
    fun parallelUnauthorizedShareOneIssue() = runTest {
        var accepted = "Bearer t1"
        val client = client(accepts = { it == accepted })
        client.get("/v1/users/me")
        accepted = "Bearer t2"

        val statuses = (1..8).map { async { client.get("/v1/med-kits").status } }.awaitAll()

        assertEquals(List(8) { HttpStatusCode.OK }, statuses)
        assertEquals(2, tokenCalls.get())
    }

    @Test
    fun withoutAnAccountNothingIsIssuedAndTheServerRefuses() = runTest {
        val response = client(stored = StoredAccount.Absent).get("/v1/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, tokenCalls.get())
        assertNull(seenAuthorization.single())
    }

    @Test
    fun unreadableAccountIsNotPresentedToTheServer() = runTest {
        client(stored = StoredAccount.Unreadable).get("/v1/users/me")

        assertEquals(0, tokenCalls.get())
    }

    @Test
    fun rejectedAccountEndsInUnauthorized() = runTest {
        val response = client(tokenStatus = HttpStatusCode.Unauthorized).get("/v1/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun tokenEndpointDownIsNotAnUnauthorizedAccount() = runTest {
        val failure = runCatching {
            client(tokenStatus = HttpStatusCode.ServiceUnavailable).get("/v1/users/me")
        }.exceptionOrNull()

        assertTrue(failure is AccessTokenUnavailable)
        assertEquals(0, resourceCalls.get())
    }

    @Test
    fun registrationGoesWithoutBearer() = runTest {
        client().post("/v1/auth/register")

        assertEquals(0, tokenCalls.get())
        assertNull(seenAuthorization.single())
    }
}
