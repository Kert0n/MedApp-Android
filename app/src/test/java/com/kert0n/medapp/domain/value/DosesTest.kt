package com.kert0n.medapp.domain.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kert0n.medapp.domain.value.doses

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
        assertEquals(0.doses, 3.doses.minusOrNone(4.doses))
        assertEquals(1.doses, 4.doses.minusOrNone(3.doses))
    }

    @Test
    fun dosesAddUpAndCompare() {
        assertEquals(9.doses, 5.doses + 4.doses)
        assertEquals(4.doses, minOf(5.doses, 4.doses))
        assertTrue(0.doses.isNone)
        assertTrue(3.doses-1.doses==2.doses)
    }

    @Test
    fun wholeDosesOnlyBecauseADoseIsNotSplitBetweenPacks() {
        // По одной таблетке в двух пачках при дозе в две таблетки дают ноль доз, а не одну.
        val unitId = TABLETS_FOR_TEST
        val dose = Dose(Quantity(java.math.BigDecimal("2"), unitId))
        assertEquals(0.doses, Quantity(java.math.BigDecimal("1"), unitId).dosesIn(dose))
        assertEquals(3.doses, Quantity(java.math.BigDecimal("7"), unitId).dosesIn(dose))
    }

    private companion object {
        val TABLETS_FOR_TEST = kotlin.uuid.Uuid.parse("00000000-0000-4000-8000-000000000001")
    }
}
