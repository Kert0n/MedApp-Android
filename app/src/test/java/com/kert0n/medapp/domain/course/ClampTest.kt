package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.medicine
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Нехватка и уменьшившаяся потребность зажимают выделение: каждой пачке — не больше целых доз,
 * что в ней есть, избыток снимается с конца, а расписание не трогается (PLAN D5, C1).
 */
class ClampTest {

    private val dose = tablets("2")

    private val twoPacks = medicine(source(PACK, 5), source(OTHER_PACK, 4))

    private val plenty = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun CourseMedicine.allocations() = sources.map { it.allocatedDoses }

    @Test
    fun shortageLowersOnlyThePackThatLostStock() {
        // В первой пачке четыре таблетки — две дозы: выделение зажато до двух, вторая не тронута.
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("12"))
        val clamped = twoPacks.clampedTo(dose, doses(28), shrunk)
        assertEquals(listOf(doses(2), doses(4)), clamped.allocations())
    }

    @Test
    fun clampingChangesOnlyAllocations() {
        // Зажать можно только выделение: дозы и расписания у препарата нет, и сократить курс
        // «до возможного срока» нечем.
        val course = activeCourse(sources = twoPacks.sources)
        val empty = availability(PACK to tablets("0"), OTHER_PACK to tablets("0"))
        val clamped = course.medicine.clampedTo(course.dose, doses(28), empty)
        val after = clamped.sources.fold(course) { acc, it ->
            acc.allocate(it.packageId, it.allocatedDoses, LATER)
        }
        assertEquals(listOf(doses(0), doses(0)), after.medicine.allocations())
        assertEquals(course.schedule, after.schedule)
        assertEquals(course.dose, after.dose)
        assertEquals(course.formId, after.formId)
        assertEquals(course.status, after.status)
    }

    @Test
    fun shortageShowsUpAsCoverageAndNamesTheFirstGap() {
        // Человеку — «нужно 7, обеспечено 2, не хватает с третьего приёма», а не сдвинутые даты.
        val week = schedule()
        val plan = week.occurrences(
            from = week.start.atStartOfDay(MOSCOW).toInstant(),
            until = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("0"))
        val found = twoPacks.clampedTo(dose, doses(plan.size), shrunk).coverage(dose, plan, shrunk)
        assertEquals(doses(7), found.requiredDoses)
        assertEquals(doses(2), found.coveredDoses)
        assertEquals(plan[2].at, found.firstUncoveredAt)
    }

    @Test
    fun unknownAvailabilityKeepsTheLastAllocation() {
        // Без числа снижать выделение догадкой нельзя; обеспечение при этом требует сверки.
        val partial = availability(OTHER_PACK to tablets("12"))
        val clamped28 = twoPacks.clampedTo(dose, doses(28), partial)
        assertEquals(listOf(doses(5), doses(4)), clamped28.allocations())
        assertTrue(twoPacks.coverage(dose, emptyList(), partial).requiresRecount)
    }

    @Test
    fun grownStockDoesNotRaiseTheAllocationByItself() {
        // Выделение — решение человека, а не следствие поставки.
        val grown = availability(PACK to tablets("100"), OTHER_PACK to tablets("100"))
        val clamped28 = twoPacks.clampedTo(dose, doses(28), grown)
        assertEquals(listOf(doses(5), doses(4)), clamped28.allocations())
    }

    @Test
    fun packageHintDoesNotChangeTheClamp() {
        // Доза-подсказка упаковки личная и к лечению отношения не имеет (PLAN D5, C1).
        val hinted = pack(quantity = tablets("20"))
            .describe(factsOf(pack()).copy(defaultIntakeAmount = tablets("1")))
        val available = availability(PACK to hinted.quantity, OTHER_PACK to tablets("12"))
        val clamped = twoPacks.clampedTo(dose, doses(28), available)
        assertEquals(listOf(doses(5), doses(4)), clamped.allocations())
    }

    @Test
    fun skipReleasesADoseFromTheEndOfTheMedicine() {
        // Пропуск уменьшил потребность с девяти до восьми: освободилась доза нижней пачки, а та,
        // из которой человек принимает, не тронута.
        val clamped = twoPacks.clampedTo(dose, doses(8), plenty)
        assertEquals(listOf(doses(5), doses(3)), clamped.allocations())
    }

    @Test
    fun excessOverTheNeedIsTakenFromTheEnd() {
        val clamped = twoPacks.clampedTo(dose, doses(6), plenty)
        assertEquals(listOf(doses(5), doses(1)), clamped.allocations())
    }

    @Test
    fun trimmingWalksUpWhenTheTailIsExhausted() {
        val clamped4 = twoPacks.clampedTo(dose, doses(4), plenty)
        assertEquals(listOf(doses(4), doses(0)), clamped4.allocations())
        val clamped0 = twoPacks.clampedTo(dose, doses(0), plenty)
        assertEquals(listOf(doses(0), doses(0)), clamped0.allocations())
    }

    @Test
    fun allocationWithinTheNeedIsLeftAlone() {
        assertEquals(twoPacks, twoPacks.clampedTo(dose, doses(9), plenty))
        assertEquals(twoPacks, twoPacks.clampedTo(dose, doses(28), plenty))
    }

    @Test
    fun trimmingNeverRaisesAnAllocation() {
        val small = medicine(source(PACK, 1))
        assertEquals(listOf(doses(1)), small.clampedTo(dose, doses(28), plenty).allocations())
    }

    @Test
    fun emptyMedicineSurvivesTheClamp() {
        assertEquals(medicine(), medicine().clampedTo(dose, doses(5), plenty))
    }

    @Test
    fun orderAndPackagesAreUntouched() {
        val clamped = twoPacks.clampedTo(dose, doses(6), plenty)
        assertEquals(listOf(PACK, OTHER_PACK), clamped.sources.map { it.packageId })
    }
}
