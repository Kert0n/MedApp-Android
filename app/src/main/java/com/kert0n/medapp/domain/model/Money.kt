package com.kert0n.medapp.domain.model

import java.math.BigDecimal

/** Копейки: цена пачки, а не курс валют. */
const val MONEY_SCALE = 2

const val MONEY_MAX_INPUT_LENGTH = 16

private val CURRENCY_CODE = Regex("^[A-Z]{3}$")

/**
 * Цена всей пачки. Десятичная строка по тому же правилу, что количество: одно правило хранения
 * на обе величины, без «минимальных единиц» и целочисленных копеек (PLAN D1, F3).
 *
 * Арифметики нет намеренно. Суммировать цены в разных валютах нельзя (PLAN H1), а складывать
 * одинаковые понадобится только сводке PR 17 — там сумма и будет посчитана явно, с проверкой
 * валюты, вместо оператора, которым легко сложить рубли с чем угодно.
 */
data class Money(val amount: BigDecimal, val currencyCode: String = "RUB") {

    init {
        require(amount.signum() >= 0) { "цена не бывает отрицательной" }
        require(amount.scale() <= MONEY_SCALE) { "после точки не больше $MONEY_SCALE знаков" }
        require(amount.precision() - amount.scale() <= QUANTITY_MAX_INTEGER_DIGITS) {
            "до точки не больше $QUANTITY_MAX_INTEGER_DIGITS разрядов"
        }
        require(CURRENCY_CODE.matches(currencyCode)) {
            "код валюты — три заглавные латинские буквы, а не «$currencyCode»"
        }
    }

    fun toWire(): String = amount.toPlainString()

    companion object {

        /** Как у количества: запятая становится точкой, отказ возвращается, а не бросается. */
        fun parse(input: String, currencyCode: String = "RUB"): Result<Money> {
            val text = input.trim().replace(',', '.')
            return when {
                text.isEmpty() -> failure("цена не введена")
                text.length > MONEY_MAX_INPUT_LENGTH -> failure("ввод слишком длинный")
                !DECIMAL_INPUT_FOR_MONEY.matches(text) ->
                    failure("цена — десятичное число без знака и экспоненты")

                else -> runCatching { Money(BigDecimal(text), currencyCode) }
            }
        }

        private fun failure(reason: String): Result<Money> =
            Result.failure(IllegalArgumentException(reason))
    }
}

private val DECIMAL_INPUT_FOR_MONEY = Regex("""^\d+(\.\d{1,2})?$""")
