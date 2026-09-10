package com.kert0n.medapp.domain.value

import java.math.BigDecimal

/** Обязательный текст: непустой и не длиннее [maxLength]. */
internal fun requireText(value: String, maxLength: Int, field: String) {
    require(value.isNotBlank()) { "$field: пустое значение не является сведением" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}

/**
 * Необязательный текст: `null` или непустой текст не длиннее [maxLength]. Пустая строка
 * сведением не является — отсутствие в домене выражается только `null`.
 */
internal fun requireOptionalText(value: String?, maxLength: Int, field: String) {
    if (value != null) requireText(value, maxLength, field)
}

/**
 * Десятичная величина в пределах, которые называет её владелец: не больше [maxScale] знаков после
 * точки и [maxIntegerDigits] до неё.
 */
internal fun requireDecimalWithinLimits(
    amount: BigDecimal,
    field: String,
    maxScale: Int,
    maxIntegerDigits: Int
) {
    require(amount.scale() <= maxScale) { "$field: после точки не больше $maxScale знаков" }
    // У нуля один разряд до точки при любом показателе: 0E+13 — тот же ноль, что 0.
    val integerDigits = if (amount.signum() == 0) 1 else amount.precision() - amount.scale()
    require(integerDigits <= maxIntegerDigits) {
        "$field: до точки не больше $maxIntegerDigits разрядов"
    }
}

/** То же, что [requireDecimalWithinLimits], плюс неотрицательность. */
internal fun requireNonNegativeDecimal(
    amount: BigDecimal,
    field: String,
    maxScale: Int,
    maxIntegerDigits: Int
) {
    require(amount.signum() >= 0) { "$field: значение не бывает отрицательным" }
    requireDecimalWithinLimits(amount, field, maxScale, maxIntegerDigits)
}
