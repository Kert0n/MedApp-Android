package com.kert0n.medapp.domain.model.value

import java.math.BigDecimal
import java.util.Currency

/**
 * Пока всё считается в рублях, но валюта — поле, а не допущение: разрядность у валют разная,
 * и знает её [Currency], а не константа рядом с кодом.
 */
val DEFAULT_CURRENCY: Currency = Currency.getInstance("RUB")

/**
 * Предел целой части цены — свой, хотя по значению совпадает с количеством.
 *
 * Совпадение не делает его тем же правилом: цена на сервер не уезжает, и её границу задаёт продукт
 * (PLAN C1), а не `numeric(19, 6)`. Раньше она бралась из `QUANTITY_MAX_INTEGER_DIGITS`, и
 * изменение серверного предела молча изменило бы допустимые цены.
 */
const val MONEY_MAX_INTEGER_DIGITS = 13

/**
 * Цена всей пачки. `BigDecimal` по тому же правилу, что количество, без «минимальных единиц» и
 * целочисленных копеек: одно правило на обе величины (PLAN D1). Как она станет строкой для базы —
 * дело конвертера хранения, как строкой для ввода — дело адаптера.
 *
 * Разрядность берётся у валюты, а не зашита двойкой: у рубля два знака, у иены ноль, у динара
 * три. Пока весь продукт считает в рублях, но место под расширение остаётся настоящим, а не
 * обещанным.
 *
 * Арифметики нет намеренно. Суммировать цены в разных валютах нельзя (PLAN H1), а складывать
 * одинаковые понадобится только сводке PR 17 — там сумма и будет посчитана явно, с проверкой
 * валюты, вместо оператора, которым легко сложить рубли с чем угодно.
 */
data class Money(val amount: BigDecimal, val currency: Currency = DEFAULT_CURRENCY) {

    init {
        val fractionDigits = currency.defaultFractionDigits
        require(fractionDigits >= 0) { "у ${currency.currencyCode} нет расчётной дробной части" }
        requireNonNegativeDecimal(
            amount = amount,
            field = "цена в ${currency.currencyCode}",
            maxScale = fractionDigits,
            maxIntegerDigits = MONEY_MAX_INTEGER_DIGITS
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
}
