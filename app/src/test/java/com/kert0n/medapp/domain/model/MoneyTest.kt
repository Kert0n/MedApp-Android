package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.util.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цена как величина: что допустимо, решает валюта. Разбор строк проверяется в `core/format` —
 * это другой слой и другой тест.
 */
class MoneyTest {

    private val yen: Currency = Currency.getInstance("JPY")
    private val dinar: Currency = Currency.getInstance("KWD")

    @Test
    fun defaultCurrencyIsRouble() {
        assertEquals("RUB", Money(BigDecimal("199.99")).currencyCode)
    }

    @Test
    fun sameNumberInDifferentScalesIsOnePrice() {
        assertEquals(Money(BigDecimal("120")), Money(BigDecimal("120.00")))
        assertEquals(Money(BigDecimal("120")).hashCode(), Money(BigDecimal("120.00")).hashCode())
    }

    @Test
    fun sameNumberInDifferentCurrenciesIsNotOnePrice() {
        assertTrue(Money(BigDecimal("150"), yen) != Money(BigDecimal("150")))
    }

    @Test
    fun currencyKnowsItsOwnFractionDigits() {
        // У иены дробной части в расчётах нет, у динара её три знака.
        assertEquals(BigDecimal("150"), Money(BigDecimal("150"), yen).amount)
        assertEquals(BigDecimal("1.500"), Money(BigDecimal("1.500"), dinar).amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fractionalYenIsRejected() {
        Money(BigDecimal("1.50"), yen)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirdFractionDigitOfARoubleIsRejected() {
        Money(BigDecimal("1.005"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePriceIsRejected() {
        Money(BigDecimal("-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownCurrencyCodeIsRejectedByTheComponentItself() {
        Currency.getInstance("rub")
    }
}
