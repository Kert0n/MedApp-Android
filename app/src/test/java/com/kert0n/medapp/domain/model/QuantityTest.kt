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
    fun zeroIsZeroWhateverItsExponent() {
        // `0E+13` — тот же ноль, что `0`, и у него один разряд до точки. Считать разряды по
        // `precision() - scale()` значило бы отвергнуть величину за запись её нуля.
        assertTrue(tablets("0E+13").isZero)
        assertEquals(tablets("0"), tablets("0E+13"))
    }

    @Test
    fun zeroIsAllowedBecauseStockRunsOut() {
        assertTrue(Quantity.zero(TABLETS).isZero)
        assertFalse(tablets("0.000001").isZero)
    }

    @Test
    fun largeAndSmallAmountsKeepTheirValue() {
        // Формат строки задаёт адаптер; величина хранит число и не теряет его на границах.
        assertEquals(BigDecimal("0.000001"), tablets("0.000001").amount)
        assertEquals(0, tablets("1E+12").amount.compareTo(BigDecimal("1000000000000")))
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
