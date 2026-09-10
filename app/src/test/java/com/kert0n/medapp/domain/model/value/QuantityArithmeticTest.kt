package com.kert0n.medapp.domain.model.value

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.tablets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Арифметика количеств. Нехватка — ошибка, а не молчаливый ноль; доза делится нацело, потому
 * что плановая доза берётся из одной упаковки и между пачками не делится (PLAN D1, D5).
 */
class QuantityArithmeticTest {

    @Test
    fun decimalAdditionDoesNotDrift() {
        assertEquals(tablets("0.3"), tablets("0.1") + tablets("0.2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentUnitsDoNotAdd() {
        tablets("1") + millilitres("1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentUnitsDoNotCompare() {
        tablets("1").covers(millilitres("1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun takingMoreThanIsLeftThrows() {
        tablets("3") - tablets("5")
    }

    @Test
    fun displayClampsShortfallToZero() {
        assertEquals(Quantity.zero(TABLETS), tablets("3").minusOrZero(tablets("5")))
        assertEquals(tablets("2"), tablets("5").minusOrZero(tablets("3")))
    }

    @Test
    fun subtractingToTheLastUnitIsAllowed() {
        assertTrue((tablets("5") - tablets("5")).isZero)
    }

    @Test
    fun coversIsInclusiveAtEquality() {
        assertTrue(tablets("2").covers(tablets("2")))
        assertFalse(tablets("1.999999").covers(tablets("2")))
    }

    @Test
    fun timesCountsDoses() {
        assertEquals(tablets("7.5"), tablets("2.5") * doses(3))
        assertTrue((tablets("2.5") * Doses.none).isZero)
    }

    @Test(expected = IllegalArgumentException::class)
    fun timesOverflowingThirteenDigitsThrowsInsteadOfRounding() {
        tablets("1000000000000") * doses(100)
    }

    @Test
    fun wholeDosesOnly() {
        assertEquals(doses(2), tablets("5").dosesIn(tablets("2")))
        assertEquals(doses(2), tablets("5.999999").dosesIn(tablets("2")))
    }

    @Test
    fun halfADoseIsNoDoseAtAll() {
        // Именно это снимает тупик из D5: по одной таблетке в двух пачках при дозе в две
        // не дают ни одной дозы, и выделять их не во что.
        assertEquals(doses(0), tablets("1").dosesIn(tablets("2")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDoseDoesNotDivide() {
        tablets("5").dosesIn(Quantity.zero(TABLETS))
    }

    @Test
    fun dosesAreClampedToIntRange() {
        assertEquals(doses(Int.MAX_VALUE), tablets("1000000000000").dosesIn(tablets("0.000001")))
    }
}
