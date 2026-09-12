package com.kert0n.medapp.domain.account

import com.kert0n.medapp.domain.Unavailability

/**
 * Знакомо ли устройство серверу. Без этого показывать нечего: без пропуска нет ни словарей, ни
 * справочника, и первый запуск требует сети (PLAN C3).
 *
 * [KeyLost] отделён намеренно: сохранённое есть, но не открывается, и завести вторую учётную
 * запись поверх локальных данных молча нельзя — это решение человека (PLAN G2). Сведённый к
 * обычному отказу, этот случай получил бы «повторить», который перерегистрирует.
 */
sealed interface AccountReadiness {

    data object Ready : AccountReadiness

    data object KeyLost : AccountReadiness

    data class NotReady(val reason: Unavailability) : AccountReadiness
}
