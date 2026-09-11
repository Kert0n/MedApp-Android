package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.doses
import com.kert0n.medapp.fixture.BERLIN
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.beginning
import com.kert0n.medapp.fixture.schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Сколько осталось — знает курс: назначенное число доз за вычетом принятых. Календарь только
 * раскладывает их по дням, а окно материализации в шестьдесят дней (PLAN F4) полного размера
 * плана не знает и знать не должно.
 */
class CourseScheduleRemainingTest {

    private val start: LocalDate = LocalDate.of(2027, 3, 1).with(DayOfWeek.MONDAY)

    private val fourTimesADay = schedule(
        start = start,
        times = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(22, 0))
    )

    @Test
    fun wholeYearIsCountedBeyondTheSixtyDayWindow() {
        // 365 дней по четыре приёма: окно бы дало 240, а назначено 1460 — и все впереди.
        val year = activeCourse(schedule = fourTimesADay, totalDoses = 365 * 4)
        assertEquals((365 * 4).doses, year.remainingDoses(taken = 0.doses))
        assertEquals(start.plusDays(364), year.expectedEnd(0.doses, fourTimesADay.beginning)?.localDate)
    }

    @Test
    fun takenDosesAreNotNeededAgain() {
        val week = activeCourse()
        assertEquals(5.doses, week.remainingDoses(taken = 2.doses))
    }

    @Test
    fun aMissedDoseIsStillNeededAndMovesTheEnd() {
        // Пропуск не уменьшает потребность — потребность уезжает вперёд.
        val week = activeCourse()
        val afterAMiss = week.schedule.beginning.plusSeconds(86_400)
        assertEquals(7.doses, week.remainingDoses(taken = 0.doses))
        assertEquals(week.schedule.start.plusDays(7), week.expectedEnd(0.doses, afterAMiss)?.localDate)
    }

    @Test
    fun finishedTreatmentNeedsNothing() {
        val week = activeCourse()
        assertEquals(0.doses, week.remainingDoses(taken = 7.doses))
        assertNull(week.expectedEnd(7.doses, week.schedule.beginning))
        // Принято больше назначенного — потребность ноль, а не долг.
        assertEquals(0.doses, week.remainingDoses(taken = 9.doses))
    }

    @Test
    fun shorteningTheTotalEndsTheTreatmentEarlierAndRaisesTheRevision() {
        // Хочет закончить раньше — сокращает число доз рукой; отдельного «отказался» не нужно.
        val week = activeCourse()
        val shortened = week.setTotalDoses(3.doses, week.updatedAt.plusSeconds(1))
        assertEquals(3.doses, shortened.totalDoses)
        assertEquals(week.revision.next(), shortened.revision)
        assertEquals(week.schedule.start.plusDays(2), shortened.expectedEnd(0.doses, week.schedule.beginning)?.localDate)
        assertEquals(week.schedule.beginning.plusSeconds(1).let { shortened.updatedAt }, shortened.updatedAt)
    }

    @Test
    fun twoDosesInsideOneMissingHourStayTwoDoses() {
        // Оба времени сдвигаются в один момент; это разные назначенные пункты, и оба нужны.
        val transition = LocalDate.of(2027, 3, 28)
        val day = schedule(
            start = transition,
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            times = listOf(LocalTime.of(2, 15), LocalTime.of(2, 45)),
            zone = BERLIN
        )
        val found = day.next(day.beginning, 2)
        assertEquals(listOf(LocalTime.of(2, 15), LocalTime.of(2, 45)), found.map { it.localTime })
        assertEquals(found[0].at, found[1].at)
    }

    @Test
    fun remainingOccurrencesAreCountedFromTheGivenMoment() {
        val week = activeCourse()
        val fromMidWeek = week.schedule.start.plusDays(3).atStartOfDay(MOSCOW).toInstant()
        val ahead = week.remainingOccurrences(taken = 3.doses, from = fromMidWeek)
        assertEquals(4, ahead.size)
        assertEquals(week.schedule.start.plusDays(3), ahead.first().localDate)
        assertEquals(week.schedule.start.plusDays(6), ahead.last().localDate)
    }
}
