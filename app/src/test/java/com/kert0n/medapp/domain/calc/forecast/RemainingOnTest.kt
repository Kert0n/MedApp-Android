package com.kert0n.medapp.domain.calc.forecast

import com.kert0n.medapp.domain.model.pack.Claims
import com.kert0n.medapp.domain.model.pack.PackageStock
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_ON
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Прогноз считается формулой, а не строками приёмов (PLAN H1, F4). */
class RemainingOnTest {

    /** Курс начинается в тот же день, что и отсчёт: неделя по одному приёму в девять утра. */
    private val week = schedule()

    private val now = week.start.atStartOfDay(MOSCOW).toInstant()

    private val today: LocalDate = FIRST_SCHEDULED_ON

    private fun stockOf(
        id: Uuid = PACK,
        quantity: String = "20",
        claims: Claims? = null,
        unresolved: List<Uuid> = emptyList(),
        expiresOn: LocalDate? = null
    ) = PackageStock(
        pkg = pack(id = id, quantity = tablets(quantity), claims = claims, expiresOn = expiresOn),
        today = today,
        unresolvedOperationIds = unresolved
    )

    private val course = activeCourse(schedule = week, sources = listOf(source(PACK, 7)))

    @Test
    fun futureIntakesAreSubtractedForTheWholeInclusiveDay() {
        // Три дня прогноза: приёмы первого, второго и третьего дня уже вычтены — дата
        // включительна в зоне отчёта.
        val forecast = remainingOn(
            date = today.plusDays(2),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertEquals(tablets("14"), forecast.single().remaining)
        assertEquals(today.plusDays(3).atStartOfDay(MOSCOW).toInstant(), forecast.single().at)
    }

    @Test
    fun answeredIntakesAreNotCountedTwice() {
        // Расход подтверждённого приёма уже в остатке: вычесть его снова значило бы списать
        // вчерашнюю таблетку второй раз.
        val taken = plannedIntake().confirm(PACK, HOME_KIT, tablets("2"), LATER)
        val forecast = remainingOn(
            date = today.plusDays(2),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(course),
            resolved = listOf(taken)
        )
        assertEquals(tablets("16"), forecast.single().remaining)
    }

    @Test
    fun skippedIntakeSpendsNothing() {
        val skipped = plannedIntake().skip(LATER)
        val forecast = remainingOn(
            date = today.plusDays(2),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(course),
            resolved = listOf(skipped)
        )
        assertEquals(tablets("16"), forecast.single().remaining)
    }

    @Test
    fun onlyCoveredIntakesAreSpent() {
        // Выделено две дозы на семь приёмов: прогноз списывает две, а не «сколько по календарю».
        val short = activeCourse(schedule = week, sources = listOf(source(PACK, 2)))
        val forecast = remainingOn(
            date = today.plusDays(6),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(short),
            resolved = emptyList()
        )
        assertEquals(tablets("16"), forecast.single().remaining)
    }

    @Test
    fun stackIsSpentTopDownAcrossPackages() {
        // Две дозы из первой пачки, дальше вторая. Остаток первой не переливается.
        val twoSources = activeCourse(
            schedule = week,
            sources = listOf(source(PACK, 2), source(OTHER_PACK, 5))
        )
        val forecast = remainingOn(
            date = today.plusDays(6),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf(), stockOf(id = OTHER_PACK, quantity = "12")),
            courses = listOf(twoSources),
            resolved = emptyList()
        )
        assertEquals(tablets("16"), forecast.first { it.packageId == PACK }.remaining)
        assertEquals(tablets("2"), forecast.first { it.packageId == OTHER_PACK }.remaining)
    }

    @Test
    fun unresolvedOperationLeavesNoInventedRemainder() {
        // При требуемой сверке выдуманный остаток не рисуется: ноль и «неизвестно» — разные
        // ответы (PLAN D4).
        val forecast = remainingOn(
            date = today.plusDays(2),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf(unresolved = listOf(INTAKE))),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertNull(forecast.single().remaining)
        assertTrue(forecast.single().requiresRecount)
    }

    @Test
    fun reservedByOthersIsShownSeparatelyAndNotSubtracted() {
        // Таблетки физически лежат в пачке, просто заявлены другими людьми.
        val forecast = remainingOn(
            date = today,
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf(claims = Claims(BigDecimal("15"), BigDecimal("10")))),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertEquals(tablets("18"), forecast.single().remaining)
        assertEquals(tablets("5"), forecast.single().reservedByOthers)
    }

    @Test
    fun expiryIsMarkedAndDoesNotZeroTheAmount() {
        val forecast = remainingOn(
            date = today.plusDays(6),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf(expiresOn = today.plusDays(3))),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertTrue(forecast.single().expired)
        assertEquals(tablets("6"), forecast.single().remaining)
    }

    @Test
    fun onlyActiveCoursesSpendAnything() {
        val cancelled = course.cancel(LATER)
        val forecast = remainingOn(
            date = today.plusDays(6),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(cancelled),
            resolved = emptyList()
        )
        assertEquals(tablets("20"), forecast.single().remaining)
        assertFalse(forecast.single().requiresRecount)
    }

    @Test
    fun forecastBeyondTheWindowIsStillCalculatedByTheCalendar() {
        // Годовой курс: третий месяц лежит далеко за окном материализации, и по строкам приёмов
        // ответ был бы завышен.
        val year = schedule(
            start = today,
            endInclusive = today.plusDays(364),
            times = listOf(LocalTime.of(9, 0))
        )
        val long = activeCourse(schedule = year, sources = listOf(source(PACK, 60)))
        val forecast = remainingOn(
            date = today.plusMonths(3),
            reportZone = MOSCOW,
            now = now,
            packages = listOf(stockOf(quantity = "200")),
            courses = listOf(long),
            resolved = emptyList()
        )
        // Девяносто три дня по одному приёму, но выделено шестьдесят доз: списывается выделенное.
        assertEquals(tablets("80"), forecast.single().remaining)
    }

    @Test
    fun horizonIsThreeCalendarMonths() {
        assertThrows(IllegalArgumentException::class.java) {
            remainingOn(today.plusMonths(3).plusDays(1), MOSCOW, now, listOf(stockOf()), listOf(course), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            remainingOn(today.minusDays(1), MOSCOW, now, listOf(stockOf()), listOf(course), emptyList())
        }
    }

    @Test
    fun reportZoneDecidesWhereTheDayEnds() {
        // Приём в девять утра по Москве — это шесть утра по Гринвичу: в зоне отчёта UTC он
        // приходится на тот же день, и включительность считается по зоне отчёта.
        val utc = ZoneId.of("UTC")
        val forecast = remainingOn(
            date = today,
            reportZone = utc,
            now = now,
            packages = listOf(stockOf()),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertEquals(tablets("18"), forecast.single().remaining)
    }
}
