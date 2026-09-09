package com.kert0n.medapp.domain.model

import java.math.BigDecimal

/**
 * Механизм проверки десятичной величины. Пределы называет владелец правила и передаёт их явно.
 *
 * Значений по умолчанию здесь нет намеренно. Пока они были, цена проверялась серверным
 * `QUANTITY_MAX_INTEGER_DIGITS`, хотя на сервер она не уезжает вовсе: изменение предела количества
 * молча изменило бы допустимые цены. Помощник обязан не знать, чью величину он проверяет.
 */
internal fun requireDecimalWithinLimits(
    amount: BigDecimal,
    field: String,
    maxScale: Int,
    maxIntegerDigits: Int
) {
    require(amount.scale() <= maxScale) { "$field: после точки не больше $maxScale знаков" }
    // У нуля один разряд до точки при любом показателе степени: `0E+13` — тот же ноль, что `0`,
    // и `precision() - scale()` дал бы для него 14. Отвергать величину за запись её нуля нельзя.
    val integerDigits = if (amount.signum() == 0) 1 else amount.precision() - amount.scale()
    require(integerDigits <= maxIntegerDigits) {
        "$field: до точки не больше $maxIntegerDigits разрядов"
    }
}

/** То же плюс неотрицательность — для величин, у которых знака не бывает вовсе. */
internal fun requireNonNegativeDecimal(
    amount: BigDecimal,
    field: String,
    maxScale: Int,
    maxIntegerDigits: Int
) {
    require(amount.signum() >= 0) { "$field: значение не бывает отрицательным" }
    requireDecimalWithinLimits(amount, field, maxScale, maxIntegerDigits)
}
