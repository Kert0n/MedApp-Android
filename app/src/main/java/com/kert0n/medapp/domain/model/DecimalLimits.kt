package com.kert0n.medapp.domain.model

import java.math.BigDecimal

/**
 * Границы, в которых величина помещается в серверный `numeric(19, 6)` (PLAN B2).
 *
 * Одно место на все десятичные величины домена — количество, цену, бронь и движение остатка.
 * Порознь эти три `require` уже стояли в четырёх конструкторах, и разъехались бы они молча:
 * изменение `QUANTITY_SCALE` в одном из них прошло бы, а остальные продолжали бы жить по
 * прежнему правилу.
 *
 * [maxScale] отдельным параметром, потому что разрядность после точки — свойство величины:
 * у количества шесть знаков, у цены столько, сколько у её валюты.
 */
internal fun requireDecimalWithinLimits(
    amount: BigDecimal,
    field: String,
    maxScale: Int = QUANTITY_SCALE
) {
    require(amount.scale() <= maxScale) { "$field: после точки не больше $maxScale знаков" }
    require(amount.precision() - amount.scale() <= QUANTITY_MAX_INTEGER_DIGITS) {
        "$field: до точки не больше $QUANTITY_MAX_INTEGER_DIGITS разрядов"
    }
}

/** То же плюс неотрицательность — для величин, у которых знака не бывает вовсе. */
internal fun requireNonNegativeDecimal(
    amount: BigDecimal,
    field: String,
    maxScale: Int = QUANTITY_SCALE
) {
    require(amount.signum() >= 0) { "$field: значение не бывает отрицательным" }
    requireDecimalWithinLimits(amount, field, maxScale)
}
