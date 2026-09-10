package com.kert0n.medapp.domain.value

import org.junit.Assert.assertEquals
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
        assertThrows(IllegalArgumentException::class.java) { Doses(3) - Doses(4) }
    }

    @Test
    fun clampedSubtractionIsAskedForByName() {
        assertEquals(Doses.none, Doses(3).minusOrNone(Doses(4)))
        assertEquals(Doses(1), Doses(4).minusOrNone(Doses(3)))
    }

    @Test
    fun dosesAddUpAndCompare() {
        assertEquals(Doses(9), Doses(5) + Doses(4))
        assertEquals(Doses(4), minOf(Doses(5), Doses(4)))
        assertTrue(Doses.none.isNone)
        assertTrue(Doses.one > Doses.none)
    }

    @Test
    fun wholeDosesOnlyBecauseADoseIsNotSplitBetweenPacks() {
        // По одной таблетке в двух пачках при дозе в две таблетки дают ноль доз, а не одну.
        val unitId = TABLETS_FOR_TEST
        val dose = Quantity(java.math.BigDecimal("2"), unitId)
        assertEquals(Doses.none, Quantity(java.math.BigDecimal("1"), unitId).dosesIn(dose))
        assertEquals(Doses(3), Quantity(java.math.BigDecimal("7"), unitId).dosesIn(dose))
    }

    private companion object {
        val TABLETS_FOR_TEST = kotlin.uuid.Uuid.parse("00000000-0000-4000-8000-000000000001")
    }
}
