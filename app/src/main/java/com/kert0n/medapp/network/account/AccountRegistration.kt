package com.kert0n.medapp.network.account

import com.kert0n.medapp.di.RegistrationToken
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Первичная настройка устройства (PLAN B1, G2): учётные данные **придумывает устройство**,
 * записывает их и только потом просит сервер их запомнить. Порядок именно такой: пока данных нет
 * на устройстве, отправлять нечего, а потерянный ответ безопасен — повтор идёт теми же данными, и
 * сервер отвечает «такая уже есть». Своя ли это учётка, показывает пропуск по тем же данным.
 *
 * Нечитаемую учётку поверх не перерегистрируют — это решение человека, потому что брони на старой
 * уже не снять. Регистрация одна на всех вызывающих: два экрана, спросившие одновременно, не
 * заведут двух учёток.
 */
@Singleton
class AccountRegistration @Inject constructor(
    private val api: MedAppApi,
    private val credentials: CredentialSource,
    @RegistrationToken private val registrationToken: String
) {

    sealed interface Outcome {

        /** Учётка есть и сервер её знает — только что записана или уже была. */
        data object Ready : Outcome

        /** Сохранённое не открывается: спросить человека, а не заводить новую молча. */
        data object Unreadable : Outcome

        /**
         * Придуманные данные не легли на устройство. На сервере при этом ничего не заведено:
         * терять нечего, и повторить настройку можно.
         */
        data object NotStored : Outcome

        /** Сервер учётку не принял: ввод, токен сборки, отсутствие связи или неизвестный исход. */
        data class Failed(val failure: ApiFailure) : Outcome
    }

    private val mutex = Mutex()

    suspend fun ensure(): Outcome = mutex.withLock {
        when (val stored = credentials.read()) {
            is StoredAccount.Present -> Outcome.Ready
            StoredAccount.Unreadable -> Outcome.Unreadable
            // Записаны, но сервер о них не сказал: повтор идёт теми же данными.
            is StoredAccount.Pending -> register(stored.credentials)
            StoredAccount.Absent -> {
                val invented = AccountCredentials.random()
                when (credentials.save(invented)) {
                    CredentialsSaved.SAVED -> register(invented)
                    CredentialsSaved.LOST -> Outcome.NotStored
                }
            }
        }
    }

    private suspend fun register(account: AccountCredentials): Outcome =
        when (val registered = api.register(account, registrationToken)) {
            is ApiResult.Success -> confirmed()
            is ApiResult.Failure ->
                if (registered.failure == ApiFailure.Conflict) ours(account)
                else Outcome.Failed(registered.failure)
        }

    /**
     * Логин занят: наш ли. Пропуск по тем же данным выдан — учётка наша и запись подтверждается;
     * учётные данные не приняты — логин чужой, и это отказ, а не повод придумывать новые поверх.
     * Прочие отказы говорят не о принадлежности, а о связи, и повторяются позже.
     */
    private suspend fun ours(account: AccountCredentials): Outcome =
        when (val issued = api.token(account)) {
            is ApiResult.Success -> confirmed()
            is ApiResult.Failure ->
                if (issued.failure == ApiFailure.Unauthorized) Outcome.Failed(ApiFailure.Conflict)
                else Outcome.Failed(issued.failure)
        }

    /**
     * Подтверждение — тоже запись, и она может не лечь. Учётка при этом рабочая: следующая
     * настройка повторит регистрацию теми же данными и получит `409`, а не заведёт вторую.
     */
    private suspend fun confirmed(): Outcome {
        credentials.confirm()
        return Outcome.Ready
    }
}
