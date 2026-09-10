package com.kert0n.medapp.network.account

import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.REGISTRATION_TOKEN_HEADER
import com.kert0n.medapp.network.server.medAppHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Учётку заводят, только если её нет, и ключ сохраняют сразу; нечитаемую поверх не
 * перерегистрируют, а одновременные вызовы дают одну регистрацию (PLAN B1, G2).
 */
class AccountRegistrationTest {

    private val login = Uuid.parse("00000000-0000-4000-8000-000000000071")

    private class Memory(var account: StoredAccount) : CredentialSource {
        override suspend fun read(): StoredAccount = account
        override suspend fun save(credentials: AccountCredentials) {
            account = StoredAccount.Present(credentials)
        }
    }

    private val requests = mutableListOf<String?>()

    private fun registration(
        stored: Memory,
        status: HttpStatusCode = HttpStatusCode.OK
    ) = AccountRegistration(
        MedAppApi(
            medAppHttpClient(
                MockEngine { request ->
                    requests += request.headers[REGISTRATION_TOKEN_HEADER]
                    respond(
                        if (status == HttpStatusCode.OK) """{"login":"$login","key":"k3y-once"}""" else "",
                        status,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                },
                "https://medapp.test"
            )
        ),
        stored,
        registrationToken = "build-token"
    )

    @Test
    fun absentAccountIsRegisteredAndItsKeyKeptAtOnce() = runTest {
        val stored = Memory(StoredAccount.Absent)

        assertEquals(AccountRegistration.Outcome.Ready, registration(stored).ensure())

        assertEquals(StoredAccount.Present(AccountCredentials(login, "k3y-once")), stored.account)
        assertEquals(listOf<String?>("build-token"), requests)
    }

    @Test
    fun existingAccountIsNotRegisteredAgain() = runTest {
        val stored = Memory(StoredAccount.Present(AccountCredentials(login, "old")))

        assertEquals(AccountRegistration.Outcome.Ready, registration(stored).ensure())

        assertEquals(emptyList<String?>(), requests)
    }

    @Test
    fun unreadableAccountIsNotReplacedSilently() = runTest {
        val stored = Memory(StoredAccount.Unreadable)

        assertEquals(AccountRegistration.Outcome.Unreadable, registration(stored).ensure())

        assertEquals(emptyList<String?>(), requests)
        assertEquals(StoredAccount.Unreadable, stored.account)
    }

    @Test
    fun refusedRegistrationKeepsNothing() = runTest {
        val stored = Memory(StoredAccount.Absent)

        val outcome = registration(stored, HttpStatusCode.Forbidden).ensure()

        assertEquals(AccountRegistration.Outcome.Failed(ApiFailure.RegistrationRefused), outcome)
        assertEquals(StoredAccount.Absent, stored.account)
    }

    @Test
    fun simultaneousSetupRegistersOnce() = runTest {
        val registration = registration(Memory(StoredAccount.Absent))

        val outcomes = List(4) { async { registration.ensure() } }.awaitAll()

        assertEquals(List(4) { AccountRegistration.Outcome.Ready }, outcomes)
        assertEquals(1, requests.size)
    }
}
