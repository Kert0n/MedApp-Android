package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Значение количества: равенство по числу, а не по масштабу, и границы, за которыми величина
 * перестаёт помещаться в серверный `numeric(19, 6)` (PLAN D1, B2).
 */
class QuantityTest {

    @Test
    fun sameNumberInDifferentScalesIsOneValue() {
        assertEquals(tablets("1"), tablets("1.000000"))
        assertEquals(tablets("1").hashCode(), tablets("1.000000").hashCode())
    }

    @Test
    fun trailingZeroesDoNotChangeHashOfWholeNumbers() {
        assertEquals(tablets("100").hashCode(), tablets("100.000000").hashCode())
        assertEquals(tablets("0").hashCode(), tablets("0.000000").hashCode())
    }

    @Test
    fun sameNumberInDifferentUnitsIsNotEqual() {
        assertNotEquals(tablets("1"), millilitres("1"))
    }

    @Test
    fun zeroIsAllowedBecauseStockRunsOut() {
        assertTrue(Quantity.zero(TABLETS).isZero)
        assertFalse(tablets("0.000001").isZero)
    }

    @Test
    fun wireFormHasNoExponentAndNoSign() {
        assertEquals("0.000001", tablets("0.000001").toWire())
        assertEquals("1000000000000", tablets("1E+12").toWire())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAmountIsRejected() {
        tablets("-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun sevenFractionDigitsAreRejected() {
        tablets("0.0000001")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fourteenIntegerDigitsAreRejected() {
        tablets("12345678901234")
    }

    @Test
    fun thirteenIntegerAndSixFractionDigitsFit() {
        assertEquals(
            BigDecimal("1234567890123.123456"),
            tablets("1234567890123.123456").amount
        )
    }
}
