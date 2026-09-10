package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.requireNonNegativeDecimal
import java.math.BigDecimal

/**
 * Что заявлено на упаковку: сумма всех броней и моя часть. Отдельно от пачки, потому что картину
 * броней двигают другие люди и другие команды (PLAN B3). Единица одна и берётся у пачки.
 */
data class Claims(
    val total: BigDecimal,     // сумма всех броней; МОЖЕТ превышать остаток
    val mine: BigDecimal? = null   // моя часть; null — я ничего не заявлял
) {

    init {
        requireClaimAmount(total, "Claims.total")
        mine?.let { requireClaimAmount(it, "Claims.mine") }
    }

    /**
     * Сколько заявлено не мной (PLAN D4), не меньше нуля: моя часть — снимок сервера и может
     * отставать от моего свежего выделения, поэтому отрицательная разность законна.
     */
    val reservedByOthers: BigDecimal
        get() = (total - (mine ?: BigDecimal.ZERO)).coerceAtLeast(BigDecimal.ZERO)
}

/** Бронь живёт по серверным пределам: она приходит с сервера и туда же уезжает (PLAN B2). */
private fun requireClaimAmount(amount: BigDecimal, field: String) {
    requireNonNegativeDecimal(
        amount = amount,
        field = field,
        maxScale = Quantity.SCALE,
        maxIntegerDigits = Quantity.MAX_INTEGER_DIGITS
    )
}
