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
 * Первичная регистрация устройства (PLAN B1, G2). Учётку заводят, только если её нет, и ключ
 * сохраняют сразу же: сервер показывает его один раз. Нечитаемую учётку поверх не
 * перерегистрируют — это решение человека, потому что брони на старом ключе уже не снять.
 *
 * Регистрация одна на всех вызывающих: два экрана, спросившие одновременно, не заведут двух
 * учёток.
 */
@Singleton
class AccountRegistration @Inject constructor(
    private val api: MedAppApi,
    private val credentials: CredentialSource,
    @RegistrationToken private val registrationToken: String
) {

    sealed interface Outcome {

        /** Учётка есть — только что заведена или уже была. */
        data object Ready : Outcome

        /** Сохранённое не открывается: спросить человека, а не заводить новую молча. */
        data object Unreadable : Outcome

        /**
         * Сервер учётку не выдал. После [ApiFailure.OutcomeUnknown] повтор может оставить на
         * сервере лишнюю пустую учётку — данных на ней нет, и потерять нечего.
         */
        data class Failed(val failure: ApiFailure) : Outcome
    }

    private val mutex = Mutex()

    suspend fun ensure(): Outcome = mutex.withLock {
        when (credentials.read()) {
            is StoredAccount.Present -> Outcome.Ready
            StoredAccount.Unreadable -> Outcome.Unreadable
            StoredAccount.Absent -> when (val result = api.register(registrationToken)) {
                is ApiResult.Success -> {
                    credentials.save(AccountCredentials(result.value.login, result.value.key))
                    Outcome.Ready
                }
                is ApiResult.Failure -> Outcome.Failed(result.failure)
            }
        }
    }
}
