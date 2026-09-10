package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Пропуск освобождает выделение, а не оставляет бронь; частичная доза пересчитывает его по
 * формуле D5.
 */
class DosesAfterIntakeTest {

    private val dose = tablets("2")

    @Test
    fun fullDoseSpendsExactlyOneAllocatedDose() {
        // Пять доз по две таблетки, принято две: осталось четыре.
        assertEquals(4, dosesAfterIntake(5, dose, tablets("2"), tablets("8")))
    }

    @Test
    fun partialDoseFreesTheRestOfThatDose() {
        // Пример PLAN D5: принята одна таблетка из десяти выделенных — остаётся четыре целых
        // выделенных дозы, пятая освобождена. Половина дозы брони не держит.
        assertEquals(4, dosesAfterIntake(5, dose, tablets("1"), tablets("19")))
    }

    @Test
    fun availabilityLimitsTheAllocationToo() {
        // Второй пример PLAN D5: принято три из запаса десять — остаток семь, не более трёх доз.
        assertEquals(3, dosesAfterIntake(5, dose, tablets("3"), tablets("7")))
    }

    @Test
    fun increasedDoseTakesMoreThanOneAllocatedDose() {
        // Принято четыре таблетки — две плановые дозы: выделено остаётся три.
        assertEquals(3, dosesAfterIntake(5, dose, tablets("4"), tablets("16")))
    }

    @Test
    fun zeroAllocationIsNotRevivedByConsumption() {
        // Приём из пачки, которую человек курсу не выделял, новой брони не создаёт.
        assertEquals(0, dosesAfterIntake(0, dose, tablets("2"), tablets("18")))
    }

    @Test
    fun consumptionLargerThanTheAllocationLeavesNothing() {
        assertEquals(0, dosesAfterIntake(1, dose, tablets("5"), tablets("15")))
    }

    @Test
    fun emptyPackageLeavesNoAllocation() {
        assertEquals(0, dosesAfterIntake(5, dose, tablets("2"), tablets("0")))
    }

    @Test
    fun fractionalDoseIsHandledLikeAnyOther() {
        // Половина таблетки — законная доза: из выделенных двух с половиной таблеток (пять доз)
        // после приёма половины остаётся четыре.
        assertEquals(4, dosesAfterIntake(5, tablets("0.5"), tablets("0.5"), tablets("10")))
    }

    @Test
    fun unitsAreNotMixed() {
        assertThrows(IllegalArgumentException::class.java) {
            dosesAfterIntake(5, dose, tablets("2"), millilitres("8"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            dosesAfterIntake(5, dose, millilitres("2"), tablets("8"))
        }
    }

    @Test
    fun zeroDoseDividesNothing() {
        assertThrows(IllegalArgumentException::class.java) {
            dosesAfterIntake(5, tablets("0"), tablets("2"), tablets("8"))
        }
    }
}
