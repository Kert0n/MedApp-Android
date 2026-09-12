package com.kert0n.medapp.feature.bootstrap

import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.account.AccountRegistration
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.presentation.LoadFailure
import javax.inject.Inject

/**
 * Начало работы приложения: есть ли у устройства учётная запись и есть ли чем считать количества.
 * Пока обоих нет, показывать нечего — без пропуска нет ни словарей, ни справочника, и первый
 * запуск требует сети (PLAN C3). Экран настройки с повтором честнее пустого списка,
 * притворяющегося работающим приложением.
 *
 * Сценарий ничего не изобретает: регистрацию целиком держит [AccountRegistration], словарь —
 * [VocabularyResolver]. Здесь решается только, что из их исходов значит для человека.
 */
class AppStart @Inject constructor(
    private val registration: AccountRegistration,
    private val vocabulary: VocabularyResolver
) {

    suspend fun begin(): AppStartState = when (val outcome = registration.ensure()) {
        AccountRegistration.Outcome.Ready -> vocabularyKnown()
        // Сохранённое есть, но не открывается: молча завести вторую учётку поверх локальных
        // данных нельзя — это решение человека (PLAN G2).
        AccountRegistration.Outcome.Unreadable -> AppStartState.KeyLost
        // Не записались — значит на сервере ничего нет: исход настройки, а не сбой (PLAN G2).
        AccountRegistration.Outcome.NotStored -> AppStartState.Setup(LoadFailure.DEVICE_STORAGE)
        is AccountRegistration.Outcome.Failed -> AppStartState.Setup(outcome.failure.asLoadFailure())
    }

    /**
     * Словарь нужен, чтобы показать хоть одно количество; **свежесть** его — не условие старта.
     * Он только растёт, и уже настроенное приложение обязано открываться без связи (PLAN J3):
     * поэтому дочитывается он, лишь когда не знаем ни одной единицы.
     */
    private suspend fun vocabularyKnown(): AppStartState {
        if (vocabulary.snapshot() != Vocabulary.empty) return AppStartState.Ready
        return when (val read = vocabulary.refresh()) {
            is ApiResult.Success -> AppStartState.Ready
            is ApiResult.Failure -> AppStartState.Setup(read.failure.asLoadFailure())
        }
    }
}

/**
 * Отказ сети словами экрана. Коды и причины остаются в сети: экрану нужно знать, что показать и
 * есть ли смысл в повторе, а не то, каким статусом ответил сервер.
 */
private fun ApiFailure.asLoadFailure(): LoadFailure = when (this) {
    ApiFailure.Unavailable -> LoadFailure.NO_CONNECTION
    ApiFailure.Unauthorized, ApiFailure.RegistrationRefused -> LoadFailure.NOT_AUTHORIZED
    else -> LoadFailure.SERVER_UNAVAILABLE
}
