package com.kert0n.medapp.domain.model

import java.math.BigDecimal

/**
 * Что заявлено на упаковку: сумма всех броней и моя часть.
 *
 * Своя версия, потому что версий у упаковки две и они независимы — состояние пачки и картина
 * броней на ней двигаются разными людьми (PLAN B3). Поэтому это отдельное значение, а не поля
 * упаковки.
 *
 * Имя не `ReservationsDTO`, хотя формы совпадают: у них разное время жизни, и одинаковое имя
 * провоцировало бы подставить одно вместо другого (PLAN D). Единица здесь не хранится — она
 * одна и берётся у упаковки; вторая копия единицы могла бы с ней разойтись.
 */
data class Claims(
    val total: BigDecimal,     // сумма всех броней; МОЖЕТ превышать остаток
    val mine: BigDecimal?,     // моя часть; null — я ничего не заявлял
    val version: Long
) {

    init {
        requireClaimAmount(total, "Claims.total")
        mine?.let { requireClaimAmount(it, "Claims.mine") }
        require(version >= 0) { "версия картины броней не бывает отрицательной" }
    }

    /**
     * Сколько заявлено не мной (PLAN D4).
     *
     * Зажато нулём не для красоты: `mine` — снимок с сервера, и он отстаёт от локального
     * выделения ровно на то, что ещё не уехало. Моя свежая бронь может оказаться больше
     * известной серверу суммы, и отрицательная разность здесь — нормальное состояние, а не сбой.
     * Требовать `mine <= total` в конструкторе значило бы ронять приложение на законных данных.
     */
    val reservedByOthers: BigDecimal
        get() = (total - (mine ?: BigDecimal.ZERO)).coerceAtLeast(BigDecimal.ZERO)
}

/** Бронь живёт по серверным пределам: она приходит с сервера и туда же уезжает (PLAN B2). */
private fun requireClaimAmount(amount: BigDecimal, field: String) {
    requireNonNegativeDecimal(
        amount = amount,
        field = field,
        maxScale = QUANTITY_SCALE,
        maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
    )
}
