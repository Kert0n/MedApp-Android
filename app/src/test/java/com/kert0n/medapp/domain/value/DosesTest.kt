package com.kert0n.medapp.domain.value

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило «доз не бывает отрицательное число» проверяется здесь — на самом типе, а не девять раз
 * в каждом вычислителе, который их принимает (PLAN D5).
 */
class DosesTest {

    @Test
    fun negativeCountIsNotRepresentable() {
        assertThrows(IllegalArgumentException::class.java) { Doses(-1) }
    }

    @Test
    fun subtractionThrowsWhenThereIsNotEnough() {
        // Как у количества: «обеспечено больше, чем нужно» — ошибка расчёта, и показывать её
        // нулём значило бы её спрятать.
        assertThrows(IllegalArgumentException::class.java) { 3.doses - 4.doses }
    }

    @Test
    fun clampedSubtractionIsAskedForByName() {
        assertTrue(3.doses.minusOrNone(4.doses) == 0.doses)
        assertTrue(4.doses.minusOrNone(3.doses) == 1.doses)
    }

    @Test
    fun dosesAddUpAndCompare() {
        assertTrue(5.doses + 4.doses == 9.doses)
        assertTrue(3.doses - 1.doses == 2.doses)
        assertTrue(minOf(5.doses, 4.doses) == 4.doses)
        assertTrue(1.doses > 0.doses)
        assertTrue(0.doses.isNone)
    }

    @Test
    fun wholeDosesOnlyBecauseADoseIsNotSplitBetweenPacks() {
        // По одной таблетке в двух пачках при дозе в две таблетки дают ноль доз, а не одну.
        val unit = TABLETS_FOR_TEST
        val dose = Dose(Quantity(java.math.BigDecimal("2"), unit))
        assertTrue(Quantity(java.math.BigDecimal("1"), unit).dosesIn(dose) == 0.doses)
        assertTrue(Quantity(java.math.BigDecimal("7"), unit).dosesIn(dose) == 3.doses)
    }

    private companion object {
        val TABLETS_FOR_TEST = com.kert0n.medapp.domain.value.QuantityUnit(
            kotlin.uuid.Uuid.parse("00000000-0000-4000-8000-000000000001"), "таблетка"
        )
    }
}
