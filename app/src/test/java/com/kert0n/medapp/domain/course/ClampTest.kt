package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Нехватка и уменьшившаяся потребность зажимают выделение: каждой пачке — не больше целых доз,
 * что в ней есть, избыток снимается с конца, а расписание не трогается (PLAN D5, C1).
 */
class ClampTest {

    private val twoPacks = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)))

    private val plenty = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun PlannedCourse.allocations() = medicine.sources.map { it.allocatedDoses }

    @Test
    fun shortageLowersOnlyThePackThatLostStock() {
        // В первой пачке четыре таблетки — две дозы: выделение зажато до двух, вторая не тронута.
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("12"))
        val clamped = twoPacks.clamped(doses(28), shrunk, LATER)
        assertEquals(listOf(doses(2), doses(4)), clamped.allocations())
    }

    @Test
    fun clampingChangesOnlyAllocations() {
        // Нехватка меняет обеспечение, а не назначение: доза, расписание и форма после зажима те
        // же, и «сократить курс до возможного срока» из исходных требований невыразимо (C1).
        val empty = availability(PACK to tablets("0"), OTHER_PACK to tablets("0"))
        val after = twoPacks.clamped(doses(28), empty, LATER)
        assertEquals(listOf(doses(0), doses(0)), after.allocations())
        assertEquals(twoPacks.schedule, after.schedule)
        assertEquals(twoPacks.dose, after.dose)
        assertEquals(twoPacks.formId, after.formId)
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
        val found = twoPacks.clamped(doses(plan.size), shrunk, LATER).coverage(plan, shrunk)
        assertEquals(doses(7), found.requiredDoses)
        assertEquals(doses(2), found.coveredDoses)
        assertEquals(plan[2].at, found.firstUncoveredAt)
    }

    @Test
    fun unknownAvailabilityKeepsTheLastAllocation() {
        // Без числа снижать выделение догадкой нельзя; обеспечение при этом требует сверки.
        val partial = availability(OTHER_PACK to tablets("12"))
        val clamped28 = twoPacks.clamped(doses(28), partial, LATER)
        assertEquals(listOf(doses(5), doses(4)), clamped28.allocations())
        assertTrue(twoPacks.coverage(emptyList(), partial).requiresRecount)
    }

    @Test
    fun grownStockDoesNotRaiseTheAllocationByItself() {
        // Выделение — решение человека, а не следствие поставки.
        val grown = availability(PACK to tablets("100"), OTHER_PACK to tablets("100"))
        val clamped28 = twoPacks.clamped(doses(28), grown, LATER)
        assertEquals(listOf(doses(5), doses(4)), clamped28.allocations())
    }

    @Test
    fun packageHintDoesNotChangeTheClamp() {
        // Доза-подсказка упаковки личная и к лечению отношения не имеет (PLAN D5, C1).
        val hinted = pack(quantity = tablets("20"))
            .describe(factsOf(pack()).copy(defaultIntakeAmount = tablets("1")))
        val available = availability(PACK to hinted.quantity, OTHER_PACK to tablets("12"))
        val clamped = twoPacks.clamped(doses(28), available, LATER)
        assertEquals(listOf(doses(5), doses(4)), clamped.allocations())
    }

    @Test
    fun skipReleasesADoseFromTheEndOfTheMedicine() {
        // Пропуск уменьшил потребность с девяти до восьми: освободилась доза нижней пачки, а та,
        // из которой человек принимает, не тронута.
        val clamped = twoPacks.clamped(doses(8), plenty, LATER)
        assertEquals(listOf(doses(5), doses(3)), clamped.allocations())
    }

    @Test
    fun excessOverTheNeedIsTakenFromTheEnd() {
        val clamped = twoPacks.clamped(doses(6), plenty, LATER)
        assertEquals(listOf(doses(5), doses(1)), clamped.allocations())
    }

    @Test
    fun trimmingWalksUpWhenTheTailIsExhausted() {
        val clamped4 = twoPacks.clamped(doses(4), plenty, LATER)
        assertEquals(listOf(doses(4), doses(0)), clamped4.allocations())
        val clamped0 = twoPacks.clamped(doses(0), plenty, LATER)
        assertEquals(listOf(doses(0), doses(0)), clamped0.allocations())
    }

    @Test
    fun allocationWithinTheNeedIsLeftAlone() {
        // Тот же курс, а не копия: пересчёт идёт после каждого изменения входов, и поднимать
        // редакцию на каждом было бы шумом в истории пунктов.
        assertSame(twoPacks, twoPacks.clamped(doses(9), plenty, LATER))
        assertSame(twoPacks, twoPacks.clamped(doses(28), plenty, LATER))
    }

    @Test
    fun trimmingNeverRaisesAnAllocation() {
        val small = activeCourse(sources = listOf(source(PACK, 1)))
        assertEquals(listOf(doses(1)), small.clamped(doses(28), plenty, LATER).allocations())
    }

    @Test
    fun emptyMedicineSurvivesTheClamp() {
        val noPacks = activeCourse()
        assertSame(noPacks, noPacks.clamped(doses(5), plenty, LATER))
    }

    @Test
    fun orderAndPackagesAreUntouched() {
        val clamped = twoPacks.clamped(doses(6), plenty, LATER)
        assertEquals(listOf(PACK, OTHER_PACK), clamped.medicine.sources.map { it.packageId })
    }
}
