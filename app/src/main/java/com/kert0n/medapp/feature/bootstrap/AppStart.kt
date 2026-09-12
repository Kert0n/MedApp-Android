package com.kert0n.medapp.feature.bootstrap

import com.kert0n.medapp.domain.account.AccountReadiness
import com.kert0n.medapp.domain.account.DeviceAccount
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.domain.value.VocabularyLibrary
import javax.inject.Inject

/**
 * Начало работы приложения: есть ли у устройства учётная запись и есть ли чем считать количества.
 * Пока обоих нет, показывать нечего — без пропуска нет ни словарей, ни справочника, и первый
 * запуск требует сети (PLAN C3). Экран настройки с повтором честнее пустого списка,
 * притворяющегося работающим приложением.
 *
 * Сценарий видит доменные порты, а не сеть: знакомство с сервером — такое же действие, как
 * остальные, и кто его выполняет по проводу, здесь не знают (PLAN H1).
 */
class AppStart @Inject constructor(
    private val account: DeviceAccount,
    private val vocabulary: VocabularyLibrary
) {

    suspend fun begin(): AppStartState = when (val readiness = account.ensure()) {
        AccountReadiness.Ready -> vocabularyKnown()
        AccountReadiness.KeyLost -> AppStartState.KeyLost
        is AccountReadiness.NotReady -> AppStartState.Setup(readiness.reason)
    }

    /**
     * Словарь нужен, чтобы показать хоть одно количество; **свежесть** его — не условие старта.
     * Он только растёт, и уже настроенное приложение обязано открываться без связи (PLAN J3):
     * поэтому пополняется он, лишь когда не знаем ни одной единицы.
     */
    private suspend fun vocabularyKnown(): AppStartState {
        if (vocabulary.known() != Vocabulary.empty) return AppStartState.Ready
        val problem = vocabulary.refresh() ?: return AppStartState.Ready
        return AppStartState.Setup(problem)
    }
}
