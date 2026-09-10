package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Расписание — календарное намерение со своей зоной. Проверяются инварианты D5 и подсчёт
 * пунктов; разрешение перехода на летнее время сюда не входит — это вычисление, а не намерение.
 */
class CourseScheduleTest {

    private val monday: LocalDate = LocalDate.of(2027, 3, 1).with(DayOfWeek.MONDAY)

    @Test
    fun zoneIsPartOfTheSchedule() {
        // Перелёт не сдвигает лечение молча: «девять утра» — девять утра зоны курса, и другая
        // зона означает другое расписание, а не то же самое.
        assertNotEquals(schedule(zone = MOSCOW), schedule(zone = ZoneId.of("UTC")))
    }

    @Test
    fun oneDayScheduleIsAllowed() {
        assertEquals(1, schedule(start = monday, endInclusive = monday).occurrenceCount())
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartIsRejected() {
        schedule(start = monday, endInclusive = monday.minusDays(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyDayMaskIsRejected() {
        // Пустая маска — не «каждый день», а расписание без единого приёма.
        schedule(daysOfWeek = emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyTimesAreRejected() {
        schedule(times = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatedTimeIsRejected() {
        // Одно и то же время дважды — это один приём, а не два.
        schedule(times = listOf(LocalTime.of(9, 0), LocalTime.of(9, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsortedTimesAreRejected() {
        schedule(times = listOf(LocalTime.of(21, 0), LocalTime.of(9, 0)))
    }

    @Test
    fun everyDayScheduleCountsDaysTimesTimes() {
        val week = schedule(
            start = monday,
            endInclusive = monday.plusDays(6),
            times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
        )
        assertEquals(14, week.occurrenceCount())
    }

    @Test
    fun twoWeekdaysOverTwoFullWeeks() {
        val fortnight = schedule(
            start = monday,
            endInclusive = monday.plusDays(13),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
        )
        assertEquals(8, fortnight.occurrenceCount())
    }

    @Test
    fun tailOfAnIncompleteWeekIsCounted() {
        // Полторы недели: понедельники приходятся на первый и восьмой день.
        val nineDays = schedule(
            start = monday,
            endInclusive = monday.plusDays(8),
            daysOfWeek = setOf(DayOfWeek.MONDAY)
        )
        assertEquals(2, nineDays.occurrenceCount())
    }

    @Test
    fun tailThatMissesTheChosenDayAddsNothing() {
        // Девять дней от понедельника, но приём по пятницам: вторая пятница ещё не наступила.
        val nineDays = schedule(
            start = monday,
            endInclusive = monday.plusDays(8),
            daysOfWeek = setOf(DayOfWeek.FRIDAY)
        )
        assertEquals(1, nineDays.occurrenceCount())
    }

    @Test
    fun sameScheduleIsTheSameValue() {
        assertEquals(schedule(), schedule())
        assertEquals(schedule().hashCode(), schedule().hashCode())
    }
}
