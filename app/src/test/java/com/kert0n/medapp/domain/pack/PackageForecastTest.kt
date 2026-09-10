package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_ON
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.packAvailability
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.Instant
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
class PackageForecastTest {

    /** Курс начинается в тот же день, что и отсчёт: неделя по одному приёму в девять утра. */
    private val week = schedule()

    private val now = week.start.atStartOfDay(MOSCOW).toInstant()

    private val today: LocalDate = FIRST_SCHEDULED_ON

    private fun stockOf(
        id: Uuid = PACK,
        quantity: String = "20",
        claims: Claims? = null,
        known: Boolean = true,
        expiresOn: ExpiryDate? = null
    ) = packAvailability(
        id = id,
        quantity = tablets(quantity),
        claims = claims,
        expiresOn = expiresOn,
        known = known
    )

    private val course = activeCourse(schedule = week, sources = listOf(source(PACK, 7)))

    /**
     * Сборка, которую в приложении делает сценарий (PR 17), а домен не изображает: спросить у
     * курсов, сколько уйдёт из каждой пачки, и дать каждой пачке её число. Здесь она развёрнута
     * целиком, чтобы было видно, что ни курс не знает пачек списком, ни пачка — курсов.
     */
    private fun remainingOn(
        date: LocalDate,
        reportZone: ZoneId = MOSCOW,
        now: Instant = this.now,
        packages: List<PackageAvailability> = listOf(stockOf()),
        courses: List<Course> = listOf(course),
        resolved: List<CourseIntake> = emptyList()
    ): List<PackageForecast> {
        val until = date.plusDays(1).atStartOfDay(reportZone).toInstant()
        val availability = Availability.from(packages)
        // Пункт опознаётся курсом и назначенными датой со временем, как при материализации (F4).
        val answered = resolved
            .filter { it.status != IntakeStatus.PLANNED }
            .map { Triple(it.courseId, it.slot.localDate, it.slot.localTime) }
            .toSet()
        val spent = HashMap<Uuid, Quantity>()
        for (course in courses) {
            // Отсчёт с начала текущих суток **в зоне курса**, а не с «сейчас»: неотвеченный
            // утренний приём в полдень никуда не делся, и расход он ещё создаст (PLAN F4, D4).
            val from = now.atZone(course.schedule.zone).toLocalDate()
                .atStartOfDay(course.schedule.zone).toInstant()
            val ahead = Doses(
                course.schedule.occurrences(from, until)
                    .count { Triple(course.id, it.localDate, it.localTime) !in answered }
            )
            for ((packageId, amount) in course.spending(ahead, availability)) {
                spent[packageId] = spent[packageId]?.plus(amount) ?: amount
            }
        }
        return packages.map { it.forecastOn(date, reportZone, now, spent[it.packageId] ?: tablets("0")) }
    }

    @Test
    fun anUnansweredIntakeEarlierTodayStillCosts() {
        // Отсчёт идёт с начала суток курса: пункт девяти утра, на который не ответили, в полдень
        // из расхода не исчезает — он ещё состоится или станет пропуском.
        val noon = today.atTime(12, 0).atZone(MOSCOW).toInstant()
        val forecast = remainingOn(date = today, now = noon)
        assertEquals(tablets("18"), forecast.single().remaining)
    }

    @Test
    fun anAnsweredIntakeEarlierTodayDoesNot() {
        // А отвеченный — исчезает: его расход уже в остатке либо его не было.
        val noon = today.atTime(12, 0).atZone(MOSCOW).toInstant()
        val taken = plannedIntake().confirm(pack(), dose("2"), noon)
        val forecast = remainingOn(date = today, now = noon, resolved = listOf(taken))
        assertEquals(tablets("20"), forecast.single().remaining)
    }

    @Test
    fun futureIntakesAreSubtractedForTheWholeInclusiveDay() {
        // Три дня прогноза: приёмы первого, второго и третьего дня уже вычтены — дата
        // включительна в зоне отчёта.
        val forecast = remainingOn(
            date = today.plusDays(2),
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
        val taken = plannedIntake().confirm(pack(), dose("2"), LATER)
        val forecast = remainingOn(
            date = today.plusDays(2),
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
            packages = listOf(stockOf(known = false)),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertNull(forecast.single().remaining)
        assertTrue(forecast.single().requiresRecount)
        assertEquals(EffectiveAmount.Unknown, forecast.single().amount)
    }

    @Test
    fun unknownPackIsNotSpentAndSpendingMovesToTheNext() {
        // Первая пачка ждёт сверки: расход идёт со второй, а у первой прогноз остаётся неизвестным.
        val twoSources = activeCourse(
            schedule = week,
            sources = listOf(source(PACK, 2), source(OTHER_PACK, 5))
        )
        val forecast = remainingOn(
            date = today.plusDays(6),
            packages = listOf(stockOf(known = false), stockOf(id = OTHER_PACK, quantity = "12")),
            courses = listOf(twoSources),
            resolved = emptyList()
        )
        assertEquals(EffectiveAmount.Unknown, forecast.first { it.packageId == PACK }.amount)
        assertEquals(tablets("2"), forecast.first { it.packageId == OTHER_PACK }.remaining)
    }

    @Test
    fun reservedByOthersIsShownSeparatelyAndNotSubtracted() {
        // Таблетки физически лежат в пачке, просто заявлены другими людьми.
        val forecast = remainingOn(
            date = today,
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
            packages = listOf(stockOf(expiresOn = ExpiryDate(today.plusDays(3)))),
            courses = listOf(course),
            resolved = emptyList()
        )
        assertTrue(forecast.single().expired)
        assertEquals(tablets("6"), forecast.single().remaining)
    }

    @Test
    fun endedTreatmentSpendsNothingBecauseItsPlanIsGone() {
        // Закончившееся лечение планом быть перестаёт: отдать его прогнозу нечем, и фильтр
        // «только действующие» не нужен — отбирать не из чего.
        val forecast = remainingOn(date = today.plusDays(6), courses = emptyList())
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
            remainingOn(today.plusMonths(3).plusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            remainingOn(today.minusDays(1))
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
