package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale

/**
 * Пока всё считается в рублях, но валюта — поле, а не допущение: разрядность у валют разная,
 * и знает её [Currency], а не константа рядом с кодом.
 */
val DEFAULT_CURRENCY: Currency = Currency.getInstance("RUB")

const val MONEY_MAX_INPUT_LENGTH = 16

/**
 * Один и тот же формат с двумя разделителями: на клавиатуре печатают и «1.50», и «1,50».
 * Разбирает число `DecimalFormat`, а не мы — вместе с проверкой, что разобран весь ввод.
 * Групповой разделитель задан явно и отличается от дробного: иначе запятая в одном из форматов
 * означала бы сразу и то, и другое.
 */
private val DECIMAL_FORMATS: List<DecimalFormat> = listOf('.', ',').map { separator ->
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        decimalSeparator = separator
        groupingSeparator = if (separator == '.') ',' else '.'
    }
    DecimalFormat("0", symbols).apply {
        isParseBigDecimal = true
        isGroupingUsed = false
    }
}

/**
 * Цена всей пачки. Десятичная строка по тому же правилу, что количество: одно правило хранения
 * на обе величины, без «минимальных единиц» и целочисленных копеек (PLAN D1, F3).
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
        requireNonNegativeDecimal(amount, "цена в ${currency.currencyCode}", fractionDigits)
    }

    /** Для хранения и провода: код валюты — то, что ложится в колонку (PLAN F1). */
    val currencyCode: String get() = currency.currencyCode

    fun toWire(): String = amount.toPlainString()

    /** По значению, а не по масштабу — ровно по той же причине, что у [Quantity]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = 31 * currency.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "${toWire()} ${currency.currencyCode}"

    companion object {

        /** Как у количества: отказ возвращается, а не бросается — ввод это состояние формы. */
        fun parse(input: String, currency: Currency = DEFAULT_CURRENCY): Result<Money> {
            val text = input.trim()
            if (text.isEmpty()) return failure("цена не введена")
            if (text.length > MONEY_MAX_INPUT_LENGTH) return failure("ввод слишком длинный")
            val amount = DECIMAL_FORMATS.firstNotNullOfOrNull { wholeInput(text, it) }
                ?: return failure("цена — десятичное число без знака и экспоненты")
            return runCatching { Money(amount, currency) }
        }

        /**
         * Разобран должен быть весь ввод. `DecimalFormat` останавливается на первом непонятном
         * символе и молча отдаёт то, что успел прочитать: «1e3» иначе стало бы ценой в рубль.
         */
        private fun wholeInput(text: String, format: DecimalFormat): BigDecimal? {
            val position = ParsePosition(0)
            val parsed = format.parse(text, position) as? BigDecimal ?: return null
            return parsed.takeIf { position.index == text.length }
        }

        private fun failure(reason: String): Result<Money> =
            Result.failure(IllegalArgumentException(reason))
    }
}
