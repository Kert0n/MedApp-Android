package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.BERLIN
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переход на летнее время разрешается одним названным правилом: несуществующее время — вперёд,
 * повторяющееся — первое вхождение (PLAN D5).
 *
 * Зона в тестах называется явно и с переводом часов: на Москве, где перевода нет с 2014 года,
 * правило было бы недоказуемо.
 */
class OccurrencesTest {

    private val springForward: LocalDate = LocalDate.of(2027, 3, 28)
    private val fallBack: LocalDate = LocalDate.of(2027, 10, 31)

    private fun wholeDay(date: LocalDate, time: LocalTime, zone: java.time.ZoneId) = schedule(
        start = date,
        endInclusive = date,
        daysOfWeek = setOf(date.dayOfWeek),
        times = listOf(time),
        zone = zone
    ).occurrences(
        from = date.minusDays(1).atStartOfDay(zone).toInstant(),
        until = date.plusDays(2).atStartOfDay(zone).toInstant()
    )

    @Test
    fun missingTimeMovesForwardToTheNearestExisting() {
        // 28 марта 2027 в Берлине 02:30 не существует: часы переводят с 02:00 на 03:00.
        val found = wholeDay(springForward, LocalTime.of(2, 30), BERLIN)
        assertEquals(1, found.size)
        assertEquals(Instant.parse("2027-03-28T01:00:00Z"), found.single().at)
        // Исходное время сохраняется: тождество пункта дают назначенные дата и время.
        assertEquals(LocalTime.of(2, 30), found.single().localTime)
        assertEquals(springForward, found.single().localDate)
    }

    @Test
    fun repeatedTimeTakesTheFirstOccurrence() {
        // 31 октября 2027 в Берлине 02:30 наступает дважды: в 00:30 UTC и в 01:30 UTC.
        val found = wholeDay(fallBack, LocalTime.of(2, 30), BERLIN)
        assertEquals(1, found.size)
        assertEquals(Instant.parse("2027-10-31T00:30:00Z"), found.single().at)
    }

    @Test
    fun twoTimesInsideOneMissingHourStayTwoIntakes() {
        // Оба времени сдвигаются в один и тот же момент. Это разные назначенные пункты, и по
        // уникальности одного UTC-времени они не теряются (PLAN F4).
        val night = schedule(
            start = springForward,
            endInclusive = springForward,
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            times = listOf(LocalTime.of(2, 15), LocalTime.of(2, 45)),
            zone = BERLIN
        )
        val found = night.occurrences(
            from = springForward.atStartOfDay(BERLIN).toInstant(),
            until = springForward.plusDays(1).atStartOfDay(BERLIN).toInstant()
        )
        assertEquals(2, found.size)
        assertEquals(1, found.map { it.at }.distinct().size)
        assertEquals(listOf(LocalTime.of(2, 15), LocalTime.of(2, 45)), found.map { it.localTime })
    }

    @Test
    fun ordinaryTimeIsUnaffectedByTheTransitionDay() {
        val found = wholeDay(springForward, LocalTime.of(9, 0), BERLIN)
        assertEquals(Instant.parse("2027-03-28T07:00:00Z"), found.single().at)
    }

    @Test
    fun scheduleKeepsItsOwnZone() {
        // «Девять утра» — девять утра зоны курса, а не той, в которой оказался человек.
        val moscow = wholeDay(LocalDate.of(2027, 3, 1), LocalTime.of(9, 0), MOSCOW).single()
        assertEquals(Instant.parse("2027-03-01T06:00:00Z"), moscow.at)
    }

    @Test
    fun intervalIncludesItsStartAndExcludesItsEnd() {
        // Соседние окна материализации стыкуются без повтора и без дыры.
        val week = schedule(times = listOf(LocalTime.of(9, 0)))
        val first = ZonedDateTime.of(week.start, LocalTime.of(9, 0), MOSCOW).toInstant()
        val second = first.plusSeconds(86_400)
        assertEquals(listOf(first), week.occurrences(first, second).map { it.at })
        assertTrue(week.occurrences(first, first).isEmpty())
    }

    @Test
    fun lastDayOfTheScheduleIsIncluded() {
        // Дата конца включительная: «по тридцать первое» значит, что тридцать первое входит.
        val week = schedule()
        val found = week.occurrences(
            from = week.start.atStartOfDay(MOSCOW).toInstant(),
            until = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        assertEquals(week.endInclusive, found.last().localDate)
        assertEquals(7, found.size)
    }

    @Test
    fun nothingIsBuiltOutsideTheScheduleRange() {
        val week = schedule()
        val after = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        assertTrue(week.occurrences(after, after.plusSeconds(86_400 * 30)).isEmpty())
    }

    @Test
    fun daysOfWeekMaskIsRespected() {
        val fortnight = schedule(
            endInclusive = schedule().start.plusDays(13),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        )
        val found = fortnight.occurrences(
            from = fortnight.start.atStartOfDay(MOSCOW).toInstant(),
            until = fortnight.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        assertEquals(4, found.size)
        assertTrue(found.all { it.localDate.dayOfWeek in fortnight.daysOfWeek })
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedIntervalIsRejected() {
        val week = schedule()
        val start = week.start.atStartOfDay(MOSCOW).toInstant()
        week.occurrences(start, start.minusSeconds(1))
    }
}
