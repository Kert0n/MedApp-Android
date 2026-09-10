package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.calc.coverage.coverage
import com.kert0n.medapp.domain.calc.schedule.occurrences
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
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Нехватка зажимает выделение, не трогая расписания (PLAN D5, C1). */
class ClampAllocationsTest {

    private val course = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)))

    private val plenty = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    @Test
    fun shortageLowersOnlyTheSourceThatLostStock() {
        // В первой пачке осталось четыре таблетки — две дозы: выделение зажато до двух, второй
        // источник не тронут.
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("12"))
        assertEquals(
            listOf(doses(2), doses(4)),
            clampAllocations(course, requiredDoses = doses(28), availability = shrunk)
                .map { it.allocatedDoses }
        )
    }

    @Test
    fun recalculationTouchesNeitherScheduleNorDoseNorDates() {
        // Единственное, что функция умеет вернуть, — источники. Расписание чужим действием не
        // переписывается: «сокращаем курс до максимально возможного срока» здесь невыразимо.
        val shrunk = availability(PACK to tablets("0"), OTHER_PACK to tablets("0"))
        val clamped = clampAllocations(course, requiredDoses = doses(28), availability = shrunk)
        val after = clamped.fold(course) { acc, s -> acc.allocate(s.packageId, s.allocatedDoses, LATER) }
        assertEquals(listOf(doses(0), doses(0)), after.sources.map { it.allocatedDoses })
        assertEquals(course.schedule, after.schedule)
        assertEquals(course.doseAmount, after.doseAmount)
        assertEquals(course.unitId, after.unitId)
        assertEquals(course.formId, after.formId)
        assertEquals(course.status, after.status)
        assertEquals(course.schedule?.start, after.schedule?.start)
        assertEquals(course.schedule?.times, after.schedule?.times)
    }

    @Test
    fun shortageShowsUpAsCoverageAndNamesTheFirstGap() {
        // Сообщение человеку — «нужно 7, обеспечено 2, не хватает с такого-то приёма», а не
        // сдвинутые даты.
        val week = schedule()
        val plan = occurrences(
            schedule = week,
            from = week.start.atStartOfDay(MOSCOW).toInstant(),
            until = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("0"))
        val clamped = clampAllocations(course, requiredDoses = doses(plan.size), availability = shrunk)
        val after = clamped.fold(course) { acc, s -> acc.allocate(s.packageId, s.allocatedDoses, LATER) }
        val found = coverage(after, plan, shrunk)
        assertEquals(doses(7), found.requiredDoses)
        assertEquals(doses(2), found.coveredDoses)
        assertEquals(plan[2].at, found.firstUncoveredAt)
        assertEquals(week, after.schedule)
    }

    @Test
    fun excessOverTheRemainingNeedIsTakenFromTheEndOfTheStack() {
        assertEquals(
            listOf(doses(5), doses(1)),
            clampAllocations(course, requiredDoses = doses(6), availability = plenty)
                .map { it.allocatedDoses }
        )
    }

    @Test
    fun unknownAvailabilityKeepsTheLastAllocation() {
        // При требуемой сверке предел неизвестен: выделение сохраняется, а обеспечение помечено
        // требующим проверки — и автоматическая замена брони до сверки не отправляется.
        val partial = availability(OTHER_PACK to tablets("12"))
        val clamped = clampAllocations(course, requiredDoses = doses(28), availability = partial)
        assertEquals(listOf(doses(5), doses(4)), clamped.map { it.allocatedDoses })
        assertTrue(coverage(course, emptyList(), partial).requiresRecount)
    }

    @Test
    fun grownStockDoesNotRaiseTheAllocationByItself() {
        // Автоматического увеличения нет: выделение — решение человека, а не следствие поставки.
        val grown = availability(PACK to tablets("100"), OTHER_PACK to tablets("100"))
        assertEquals(
            listOf(doses(5), doses(4)),
            clampAllocations(course, requiredDoses = doses(28), availability = grown)
                .map { it.allocatedDoses }
        )
    }

    @Test
    fun packageHintDoesNotChangeTheCourse() {
        // Доза-подсказка упаковки независима от дозы курса: она личная и к лечению отношения не
        // имеет (PLAN D5, C1).
        val hinted = pack(quantity = tablets("20"))
            .describe(factsOf(pack()).copy(defaultIntakeAmount = tablets("1")))
        val available = availability(PACK to hinted.quantity, OTHER_PACK to tablets("12"))
        assertEquals(
            listOf(doses(5), doses(4)),
            clampAllocations(course, requiredDoses = doses(28), availability = available)
                .map { it.allocatedDoses }
        )
        assertEquals(BigDecimal("2"), course.doseAmount)
    }
}
