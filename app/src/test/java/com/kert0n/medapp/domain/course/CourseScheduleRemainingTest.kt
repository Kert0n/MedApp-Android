package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.BERLIN
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Потребность считается календарём, а не числом материализованных строк: окно в шестьдесят дней
 * (PLAN F4) не знает полного размера плана, и обеспечение по нему занизилось бы.
 */
class CourseScheduleRemainingTest {

    private val start: LocalDate = LocalDate.of(2027, 3, 1).with(DayOfWeek.MONDAY)

    private val year = schedule(
        start = start,
        endInclusive = start.plusDays(364),
        times = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(22, 0))
    )

    private fun beginning(schedule: com.kert0n.medapp.domain.course.CourseSchedule) =
        schedule.start.atStartOfDay(schedule.zone).toInstant()

    @Test
    fun wholeYearIsCountedBeyondTheSixtyDayWindow() {
        // 365 дней по четыре приёма: окно бы дало 240, а нужно 1460.
        assertEquals(365 * 4, year.countRemaining(beginning(year), emptySet()))
        assertEquals(365 * 4, year.occurrenceCount())
    }

    @Test
    fun monthWindowDoesNotLimitTheCount() {
        val month = schedule(start = start, endInclusive = start.plusDays(29))
        assertEquals(30, month.countRemaining(beginning(month), emptySet()))
    }

    @Test
    fun answeredItemsAreNotNeededAgain() {
        val week = schedule()
        val answered = setOf(
            week.start to LocalTime.of(9, 0),
            week.start.plusDays(1) to LocalTime.of(9, 0)
        )
        assertEquals(5, week.countRemaining(beginning(week), answered))
    }

    @Test
    fun futureItemCanBeAnsweredInAdvance() {
        // Пропустить будущий приём человек вправе, и потребность на него больше не считается.
        val week = schedule()
        val skippedAhead = setOf(week.endInclusive to LocalTime.of(9, 0))
        assertEquals(6, week.countRemaining(beginning(week), skippedAhead))
    }

    @Test
    fun countingStartsFromTheGivenMoment() {
        val week = schedule()
        val fromMidWeek = week.start.plusDays(3).atStartOfDay(MOSCOW).toInstant()
        assertEquals(4, week.countRemaining(fromMidWeek, emptySet()))
    }

    @Test
    fun finishedScheduleNeedsNothing() {
        val week = schedule()
        val afterEnd = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        assertEquals(0, week.countRemaining(afterEnd, emptySet()))
    }

    @Test
    fun answeredItemsAreMatchedByTheirOriginalTime() {
        // Пункт назван исходным временем, а не разрешённым моментом: иначе в день перевода часов
        // отвеченный приём посчитался бы заново.
        val transition = LocalDate.of(2027, 3, 28)
        val day = schedule(
            start = transition,
            endInclusive = transition,
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            times = listOf(LocalTime.of(2, 15), LocalTime.of(2, 45)),
            zone = BERLIN
        )
        assertEquals(2, day.countRemaining(beginning(day), emptySet()))
        assertEquals(1, day.countRemaining(beginning(day), setOf(transition to LocalTime.of(2, 15))))
    }
}
