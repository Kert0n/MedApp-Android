package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity

/**
 * Что можно утверждать о количестве в пачке: оно известно — или нет, и тогда нужна сверка
 * (PLAN E3). Как незакрытые операции ложатся на подтверждённый остаток, решает слой данных (E1),
 * домен получает готовый ответ. У [Unknown] числа нет: последнее наблюдение — это
 * `Package.quantity`, и экран показывает его сам рядом с требованием сверки.
 */
sealed interface EffectiveAmount {

    data class Known(val quantity: Quantity) : EffectiveAmount

    /** Исход операции не установлен: включён ли наш расход в серверный остаток, неизвестно. */
    data object Unknown : EffectiveAmount

    val quantityOrNull: Quantity? get() = (this as? Known)?.quantity
}
