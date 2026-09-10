package com.kert0n.medapp.network.server

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Лог debug не содержит ни заголовка авторизации, ни токена регистрации, ни ключа из тела
 * ответа регистрации (PLAN G2): маскировать только заголовок недостаточно.
 */
class SecretMaskingLoggerTest {

    private val lines = mutableListOf<String>()
    private val capture = object : Logger {
        override fun log(message: String) {
            lines += message
        }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(body: String) = medAppHttpClient(
        MockEngine { respond(body, HttpStatusCode.OK, json) },
        "https://medapp.test",
        logger = capture
    )

    private val log get() = lines.joinToString("\n")

    @Test
    fun registrationKeyAndTokenNeverReachTheLog() = runTest {
        val body = """{"login":"00000000-0000-4000-8000-000000000071","key":"k3y-shown-only-once"}"""

        val response = client(body).post("/v1/auth/register") {
            header(REGISTRATION_TOKEN_HEADER, "registration-secret")
        }

        assertEquals(body, response.bodyAsText())
        assertFalse(log.contains("k3y-shown-only-once"))
        assertFalse(log.contains("registration-secret"))
        assertTrue(log.contains("00000000-0000-4000-8000-000000000071"))
    }

    @Test
    fun accessTokenIsMaskedInBodyAndHeader() = runTest {
        client("""{"accessToken":"jwt.issued.now"}""").post("/v1/auth/token")
        client("[]").get("/v1/med-kits") { bearerAuth("jwt.issued.now") }

        assertFalse(log.contains("jwt.issued.now"))
    }

    @Test
    fun invitationKeyIsMasked() = runTest {
        client("""{"key":"invitation-secret"}""").post("/v1/med-kits/x/invitations")

        assertFalse(log.contains("invitation-secret"))
    }

    @Test
    fun credentialSchemesAreMaskedEvenOutsideHeaders() {
        val masked = SecretMaskingLogger(capture)
            .mask("Authorization: Basic bG9naW46a2V5 and Bearer eyJhbGciOi.x.y")

        assertFalse(masked.contains("bG9naW46a2V5"))
        assertFalse(masked.contains("eyJhbGciOi"))
    }

    @Test
    fun releaseBuildHasNoHttpLogAtAll() = runTest {
        medAppHttpClient(
            MockEngine { respond("""{"key":"k"}""", HttpStatusCode.OK, json) },
            "https://medapp.test"
        ).post("/v1/auth/register")

        assertTrue(lines.isEmpty())
    }
}
