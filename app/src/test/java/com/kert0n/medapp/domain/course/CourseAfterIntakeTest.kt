package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * После приёма пачке остаётся выделено не больше выделенного за вычетом расхода и не больше
 * остатка; половина дозы брони не держит (PLAN D5).
 */
class CourseAfterIntakeTest {

    private val dose = tablets("2")

    /** Пачке [PACK] выделено [allocated] доз, принято [taken], в ней осталось [left]. */
    private fun after(allocated: Int, taken: Quantity, left: Quantity, dose: Quantity = this.dose) =
        activeCourse(doseAmount = dose.amount, sources = listOf(source(PACK, allocated)))
            .dosesAfterIntake(pack(), taken, left)

    @Test
    fun fullDoseSpendsExactlyOneAllocatedDose() {
        // Пять доз по две таблетки, принято две: осталось четыре.
        assertEquals(doses(4), after(5, taken = tablets("2"), left = tablets("8")))
    }

    @Test
    fun partialDoseFreesTheRestOfThatDose() {
        // Пример PLAN D5: принята одна таблетка из десяти выделенных — четыре целых дозы.
        assertEquals(doses(4), after(5, taken = tablets("1"), left = tablets("19")))
    }

    @Test
    fun availabilityLimitsTheAllocationToo() {
        // Второй пример PLAN D5: принято три из запаса десять — остаток семь, не больше трёх доз.
        assertEquals(doses(3), after(5, taken = tablets("3"), left = tablets("7")))
    }

    @Test
    fun increasedDoseTakesMoreThanOneAllocatedDose() {
        // Принято четыре таблетки — две плановые дозы: выделено остаётся три.
        assertEquals(doses(3), after(5, taken = tablets("4"), left = tablets("16")))
    }

    @Test
    fun zeroAllocationIsNotRevivedByConsumption() {
        // Приём из пачки, которую человек курсу не выделял, брони не создаёт.
        assertEquals(doses(0), after(0, taken = tablets("2"), left = tablets("18")))
        val elsewhere = activeCourse(sources = listOf(source(OTHER_PACK, 5)))
        assertEquals(
            doses(0),
            elsewhere.dosesAfterIntake(pack(), tablets("2"), tablets("18"))
        )
    }

    @Test
    fun consumptionLargerThanTheAllocationLeavesNothing() {
        assertEquals(doses(0), after(1, taken = tablets("5"), left = tablets("15")))
    }

    @Test
    fun emptyPackageLeavesNoAllocation() {
        assertEquals(doses(0), after(5, taken = tablets("2"), left = tablets("0")))
    }

    @Test
    fun fractionalDoseIsHandledLikeAnyOther() {
        // Пять доз по половине таблетки, принята половина: остаётся четыре.
        val half = tablets("0.5")
        assertEquals(doses(4), after(5, taken = half, left = tablets("10"), dose = half))
    }

    @Test
    fun unitsAreNotMixed() {
        assertThrows(IllegalArgumentException::class.java) {
            after(5, taken = tablets("2"), left = millilitres("8"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            after(5, taken = millilitres("2"), left = tablets("8"))
        }
    }

    @Test
    fun zeroDoseIsNotTreatmentAtAll() {
        // Проверка переехала на конструктор: у курса нулевой дозы не бывает вовсе, и делить на
        // неё уже нечего.
        assertThrows(IllegalArgumentException::class.java) {
            after(5, taken = tablets("2"), left = tablets("8"), dose = tablets("0"))
        }
    }
}
