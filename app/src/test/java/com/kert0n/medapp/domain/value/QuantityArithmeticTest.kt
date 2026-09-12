package com.kert0n.medapp.domain.value

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.tablets
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
        assertTrue(tablets("0.1") + tablets("0.2") == tablets("0.3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentUnitsDoNotAdd() {
        tablets("1") + millilitres("1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentUnitsDoNotCompare() {
        tablets("1").covers(dose(millilitres("1")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun takingMoreThanIsLeftThrows() {
        tablets("3") - tablets("5")
    }

    @Test
    fun displayClampsShortfallToZero() {
        assertTrue(tablets("3").minusOrZero(tablets("5")) == Quantity.zero(TABLETS))
        assertTrue(tablets("5").minusOrZero(tablets("3")) == tablets("2"))
    }

    @Test
    fun subtractingToTheLastUnitIsAllowed() {
        assertTrue((tablets("5") - tablets("5")).isZero)
    }

    @Test
    fun coversIsInclusiveAtEquality() {
        assertTrue(tablets("2").covers(dose("2")))
        assertFalse(tablets("1.999999").covers(dose("2")))
    }

    @Test
    fun timesCountsDoses() {
        assertTrue(tablets("2.5") * 3.doses == tablets("7.5"))
        assertTrue((tablets("2.5") * 0.doses).isZero)
    }

    @Test(expected = IllegalArgumentException::class)
    fun timesOverflowingThirteenDigitsThrowsInsteadOfRounding() {
        tablets("1000000000000") * 100.doses
    }

    @Test
    fun wholeDosesOnly() {
        assertTrue(tablets("5").dosesIn(dose("2")) == 2.doses)
        assertTrue(tablets("5.999999").dosesIn(dose("2")) == 2.doses)
    }

    @Test
    fun halfADoseIsNoDoseAtAll() {
        // Именно это снимает тупик из D5: по одной таблетке в двух пачках при дозе в две
        // не дают ни одной дозы, и выделять их не во что.
        assertTrue(tablets("1").dosesIn(dose("2")) == 0.doses)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDoseIsNotRepresentable() {
        // Делить на ноль здесь нечем: правило живёт на самой дозе, а не на этом вызове.
        dose(Quantity.zero(TABLETS))
    }

    @Test
    fun dosesAreClampedToIntRange() {
        assertTrue(tablets("1000000000000").dosesIn(dose("0.000001")) == Int.MAX_VALUE.doses)
    }
}
