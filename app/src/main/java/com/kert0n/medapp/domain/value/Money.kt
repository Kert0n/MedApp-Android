package com.kert0n.medapp.domain.value

import java.math.BigDecimal
import java.util.Currency

/** Продукт считает в рублях, но валюта — поле цены, а не допущение. */
val DEFAULT_CURRENCY: Currency = Currency.getInstance("RUB")

/**
 * Цена всей пачки в своей валюте; разрядность берётся у валюты. Арифметики нет: складывать цены
 * разных валют нельзя, а сумму посчитает сводка (PR 17) с проверкой валюты.
 */
data class Money(val amount: BigDecimal, val currency: Currency = DEFAULT_CURRENCY) {

    init {
        val fractionDigits = currency.defaultFractionDigits
        require(fractionDigits >= 0) { "у ${currency.currencyCode} нет расчётной дробной части" }
        requireNonNegativeDecimal(
            amount = amount,
            field = "цена в ${currency.currencyCode}",
            maxScale = fractionDigits,
            maxIntegerDigits = MAX_INTEGER_DIGITS
        )
    }

    /** Код валюты — то, что ложится в колонку и в отчёт; сама строка суммы задаётся адаптером. */
    val currencyCode: String get() = currency.currencyCode

    /** По значению, а не по масштабу — ровно по той же причине, что у [Quantity]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = 31 * currency.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "${amount.toPlainString()} ${currency.currencyCode}"

    companion object {
        /**
         * Свой предел, хотя совпадает с количеством: цена на сервер не уезжает, и её границу
         * задаёт продукт.
         */
        const val MAX_INTEGER_DIGITS = 13
    }
}
