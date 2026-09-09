package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Цена живёт по тому же правилу, что количество: десятичная строка, два знака, без знака числа. */
class MoneyTest {

    @Test
    fun defaultCurrencyIsRouble() {
        assertEquals("RUB", Money(BigDecimal("199.99")).currencyCode)
    }

    @Test
    fun commaIsAccepted() {
        assertEquals(Money(BigDecimal("1.50")), Money.parse("1,50").getOrThrow())
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirdFractionDigitIsRejected() {
        Money(BigDecimal("1.005"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePriceIsRejected() {
        Money(BigDecimal("-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lowercaseCurrencyCodeIsRejected() {
        Money(BigDecimal("1"), "rub")
    }

    @Test
    fun parseReportsFailureInsteadOfThrowing() {
        assertTrue(Money.parse("1.005").isFailure)
        assertTrue(Money.parse("-1").isFailure)
        assertTrue(Money.parse("   ").isFailure)
        assertTrue(Money.parse("1e3").isFailure)
    }

    @Test
    fun wireFormHasNoExponent() {
        assertEquals("1000000000000", Money(BigDecimal("1E+12")).toWire())
    }
}
