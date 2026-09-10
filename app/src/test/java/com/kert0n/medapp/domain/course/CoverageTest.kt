package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Обеспечение вычисляется и называет первый непокрытый приём (PLAN D5).
 *
 * Человеку нужно не «не хватает 19», а «не хватает с такого-то приёма»: расписание при нехватке
 * не трогается, поэтому сообщить можно только про обеспечение.
 */
class CoverageTest {

    /** Неделя по четыре приёма в день — те самые 28 пунктов из приёмки PLAN. */
    private val fourTimesADay = schedule(
        times = listOf(
            LocalTime.of(9, 0),
            LocalTime.of(13, 0),
            LocalTime.of(18, 0),
            LocalTime.of(22, 0)
        )
    )

    private val remaining: List<ScheduledOccurrence> = occurrences(
        schedule = fourTimesADay,
        from = fourTimesADay.start.atStartOfDay(MOSCOW).toInstant(),
        until = fourTimesADay.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
    )

    private val availability = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun course(first: Int, second: Int) = activeCourse(
        schedule = fourTimesADay,
        sources = listOf(source(PACK, first), source(OTHER_PACK, second))
    )

    @Test
    fun twentyEightNeededWithFiveAndFourAllocatedCoversNineAndNamesTheFirstGap() {
        assertEquals(28, remaining.size)
        val found = coverage(course(first = 5, second = 4), remaining, availability)
        assertEquals(doses(28), found.requiredDoses)
        assertEquals(doses(9), found.coveredDoses)
        assertEquals(doses(19), found.missingDoses)
        assertFalse(found.isFullyCovered)
        assertEquals(remaining[8].at, found.coveredUntil)
        assertEquals(remaining[9].at, found.firstUncoveredAt)
    }

    @Test
    fun fullyCoveredCourseNamesNoGap() {
        val week = schedule()
        val plan = occurrences(
            schedule = week,
            from = week.start.atStartOfDay(MOSCOW).toInstant(),
            until = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        val enough = activeCourse(sources = listOf(source(PACK, 7)))
        val found = coverage(enough, plan, availability)
        assertTrue(found.isFullyCovered)
        assertEquals(plan.last().at, found.coveredUntil)
        assertNull(found.firstUncoveredAt)
    }

    @Test
    fun unsuppliedCourseIsCoveredFromTheVeryFirstIntake() {
        val unsupplied = activeCourse(schedule = fourTimesADay)
        val found = coverage(unsupplied, remaining, availability)
        assertEquals(doses(0), found.coveredDoses)
        assertNull(found.coveredUntil)
        assertEquals(remaining.first().at, found.firstUncoveredAt)
    }

    @Test
    fun allocationBeyondWhatThePackageGivesIsNotCoverage() {
        // Выделено девять доз, а свободно шесть таблеток — три дозы. Обеспечение честно меньше
        // выделенного, и человек видит, почему.
        val shrunk = availability(PACK to tablets("6"), OTHER_PACK to tablets("12"))
        val found = coverage(course(first = 9, second = 0), remaining, shrunk)
        assertEquals(doses(3), found.coveredDoses)
        assertEquals(doses(9), found.perSource.first().allocatedDoses)
        assertEquals(doses(3), found.perSource.first().coveredDoses)
    }

    @Test
    fun remainderSmallerThanADoseStaysInItsRowAndDoesNotSpill() {
        // По одной таблетке в двух пачках при дозе в две: ноль покрытых приёмов, и остатки
        // видны каждый в своей строке, а не сложились в одну дозу.
        val singles = availability(PACK to tablets("1"), OTHER_PACK to tablets("1"))
        val found = coverage(course(first = 5, second = 4), remaining, singles)
        assertEquals(doses(0), found.coveredDoses)
        assertEquals(listOf(tablets("1"), tablets("1")), found.perSource.map { it.leftover })
    }

    @Test
    fun leftoverIsWhatCannotMakeAWholeDose() {
        val odd = availability(PACK to tablets("5"), OTHER_PACK to tablets("12"))
        val found = coverage(course(first = 2, second = 0), remaining, odd)
        assertEquals(tablets("1"), found.perSource.first().leftover)
        assertEquals(tablets("0"), found.perSource.last().leftover)
    }

    @Test
    fun unknownAvailabilityIsNotPassedOffAsCoverage() {
        // Исход операции по первой пачке не установлен: выделение сохраняется, но обеспеченным
        // не считается, и обеспечение помечено требующим проверки.
        val found = coverage(course(first = 5, second = 4), remaining, availability(OTHER_PACK to tablets("12")))
        assertTrue(found.requiresRecount)
        assertEquals(doses(4), found.coveredDoses)
        assertEquals(doses(5), found.perSource.first().allocatedDoses)
        assertEquals(doses(0), found.perSource.first().coveredDoses)
        assertNull(found.perSource.first().leftover)
    }

    @Test
    fun coverageNeverExceedsTheNeed() {
        // Выделено больше, чем осталось приёмов: обеспечено ровно столько, сколько нужно.
        val week = schedule()
        val plan = occurrences(
            schedule = week,
            from = week.start.atStartOfDay(MOSCOW).toInstant(),
            until = week.endInclusive.plusDays(1).atStartOfDay(MOSCOW).toInstant()
        )
        val found = coverage(activeCourse(sources = listOf(source(PACK, 10))), plan, availability)
        assertEquals(doses(7), found.requiredDoses)
        assertEquals(doses(7), found.coveredDoses)
        assertNull(found.firstUncoveredAt)
    }

    @Test
    fun finishedCalendarNeedsNothing() {
        val found = coverage(course(first = 5, second = 4), emptyList(), availability)
        assertEquals(doses(0), found.requiredDoses)
        assertTrue(found.isFullyCovered)
        assertNull(found.coveredUntil)
        assertNull(found.firstUncoveredAt)
    }
}
