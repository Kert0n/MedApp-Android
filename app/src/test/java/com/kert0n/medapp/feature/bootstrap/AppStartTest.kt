package com.kert0n.medapp.feature.bootstrap

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.AccountRegistration
import com.kert0n.medapp.network.account.CredentialSource
import com.kert0n.medapp.network.account.CredentialsSaved
import com.kert0n.medapp.network.account.StoredAccount
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.medAppHttpClient
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.network.value.VocabularyStore
import com.kert0n.medapp.presentation.LoadFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Начало работы приложения (PLAN C3, G2). Проверяется решение сценария, а не чужая механика:
 * регистрацию держит `AccountRegistration`, словарь — `VocabularyResolver`, здесь — что их исходы
 * значат для человека.
 */
class AppStartTest {

    private val tablets = QuantityUnit(Uuid.parse("00000000-0000-4000-8000-000000000001"), "таблетка")

    private class Memory(var account: StoredAccount, private val writable: Boolean = true) : CredentialSource {
        override suspend fun read(): StoredAccount = account
        override suspend fun save(credentials: AccountCredentials): CredentialsSaved {
            if (!writable) return CredentialsSaved.LOST
            account = StoredAccount.Pending(credentials)
            return CredentialsSaved.SAVED
        }

        override suspend fun confirm(): CredentialsSaved {
            (account as? StoredAccount.Pending)?.let { account = StoredAccount.Present(it.credentials) }
            return CredentialsSaved.SAVED
        }
    }

    private class Words(var known: Vocabulary, val fresh: Vocabulary? = null) : VocabularyStore {
        var saved = 0
        override suspend fun snapshot(): Vocabulary = known
        override suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>) {
            saved++
            known = fresh ?: Vocabulary(units, forms)
        }
    }

    /** Сеть отвечает по-разному в зависимости от того, что проверяем. */
    private fun api(answer: (String) -> Pair<String, HttpStatusCode>) = MedAppApi(
        medAppHttpClient(
            MockEngine { request ->
                val (body, status) = answer(request.url.encodedPath)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            "https://example.invalid"
        )
    )

    private fun start(
        stored: Memory,
        words: Words,
        answer: (String) -> Pair<String, HttpStatusCode> = { path ->
            when (path) {
                "/v1/auth/token" -> """{"accessToken":"t"}""" to HttpStatusCode.OK
                "/v1/quantity-units" -> """[{"id":"${tablets.id}","name":"таблетка"}]""" to HttpStatusCode.OK
                "/v1/form-types" -> "[]" to HttpStatusCode.OK
                else -> "" to HttpStatusCode.Created
            }
        }
    ): AppStart {
        val service = api(answer)
        return AppStart(AccountRegistration(service, stored, "токен"), VocabularyResolver(words, service))
    }

    @Test
    fun aFreshDeviceRegistersAndReadsTheVocabulary() = runTest {
        val words = Words(Vocabulary.empty)

        val state = start(Memory(StoredAccount.Absent), words).begin()

        assertEquals(AppStartState.Ready, state)
        assertEquals(1, words.saved)
    }

    /**
     * Сохранённое есть, но не открывается: приложение спрашивает, а не заводит молча вторую
     * учётку поверх локальных данных (PLAN G2).
     *
     * Красная проверка: свести утрату ключа к обычному отказу — экран предложит «повторить»,
     * и повтор пойдёт регистрировать заново.
     */
    @Test
    fun aLostKeyAsksTheHumanInsteadOfRegisteringAgain() = runTest {
        val state = start(Memory(StoredAccount.Unreadable), Words(Vocabulary.empty)).begin()

        assertEquals(AppStartState.KeyLost, state)
    }

    /** Не записались — на сервере ничего нет: это исход настройки, и повторить её можно (G2). */
    @Test
    fun credentialsThatCouldNotBeStoredAreASetupOutcome() = runTest {
        val stored = Memory(StoredAccount.Absent, writable = false)

        val state = start(stored, Words(Vocabulary.empty)).begin()

        assertEquals(AppStartState.Setup(LoadFailure.DEVICE_STORAGE), state)
    }

    /** Соединение не установилось — запрос никуда не ушёл, и это отсутствие связи, а не неизвестный исход. */
    @Test
    fun withoutConnectionTheSetupScreenSaysSoAndKeepsTheRetry() = runTest {
        val state = start(Memory(StoredAccount.Absent), Words(Vocabulary.empty)) {
            throw java.net.ConnectException("связи нет")
        }.begin()

        assertEquals(AppStartState.Setup(LoadFailure.NO_CONNECTION), state)
    }

    /**
     * Настроенное приложение открывается без связи: словарь только растёт, снимок уже есть, и
     * свежесть его условием старта не является (PLAN J3).
     *
     * Красная проверка: дочитывать словарь всегда — этот случай краснеет отказом сети.
     */
    @Test
    fun anAlreadySetUpAppStartsWithoutConnection() = runTest {
        val words = Words(Vocabulary(listOf(tablets), emptyList()))
        val stored = Memory(StoredAccount.Present(AccountCredentials(Uuid.random(), "p".repeat(32))))

        val state = start(stored, words) { throw java.net.ConnectException("связи нет") }.begin()

        assertEquals(AppStartState.Ready, state)
        assertEquals(0, words.saved)
    }
}
