package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseSchedule
import com.kert0n.medapp.domain.course.CourseSource
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.course.CourseStatus
import com.kert0n.medapp.domain.course.PlannedCourse
import com.kert0n.medapp.domain.course.SourceStack
import com.kert0n.medapp.domain.value.Quantity
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.uuid.Uuid

val COURSE: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000051")

/** Момент, от которого тесты отсчитывают правки: сравнивать нужно изменение, а не «сейчас». */
val EARLIER: Instant = Instant.EPOCH

val LATER: Instant = Instant.EPOCH.plusSeconds(60)

/** Зона курса называется явно: расписание живёт в своей зоне, а не в системной (PLAN D5). */
val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

/**
 * Зона с переводом часов: в Москве его нет с 2014 года, и правило DST на ней недоказуемо.
 * В 2027 году переводы приходятся на 28 марта (вперёд) и 31 октября (назад).
 */
val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/**
 * Расписание с понятными по умолчанию значениями: неделя, все дни, один приём в девять утра.
 *
 * Начало приведено к понедельнику через [DayOfWeek], чтобы тест не зависел от того, на какой день
 * недели пришлась выбранная дата.
 */
fun schedule(
    start: LocalDate = LocalDate.of(2027, 3, 1).with(DayOfWeek.MONDAY),
    endInclusive: LocalDate = start.plusDays(6),
    daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    times: List<LocalTime> = listOf(LocalTime.of(9, 0)),
    zone: ZoneId = MOSCOW
) = CourseSchedule(
    start = start,
    endInclusive = endInclusive,
    daysOfWeek = daysOfWeek,
    times = times,
    zone = zone
)

/** Черновик: тест называет только то, что проверяет. По умолчанию — одно название. */
fun course(
    id: Uuid = COURSE,
    title: String = "Парацетамол, пять дней",
    note: String? = null,
    doseAmount: BigDecimal? = null,
    unitId: Uuid? = null,
    formId: Uuid? = null,
    schedule: CourseSchedule? = null,
    sources: List<CourseSource> = emptyList(),
    revision: Long = 0,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = CourseDraft(
    id = id,
    title = title,
    note = note,
    doseAmount = doseAmount,
    schedule = schedule,
    stack = SourceStack(items = sources, formId = formId, unitId = unitId),
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Назначенный курс: доза, единица, форма и расписание у него есть по типу, и называть их в
 * каждом тесте незачем. По умолчанию — две таблетки раз в день неделю.
 */
fun activeCourse(
    id: Uuid = COURSE,
    doseAmount: BigDecimal = BigDecimal("2"),
    unitId: Uuid = TABLETS,
    formId: Uuid = TABLET_FORM,
    schedule: CourseSchedule = schedule(),
    sources: List<CourseSource> = emptyList(),
    status: CourseStatus = CourseStatus.ACTIVE,
    revision: Long = 1,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = PlannedCourse(
    id = id,
    title = "Парацетамол, пять дней",
    dose = Quantity(doseAmount, unitId),
    schedule = schedule,
    stack = SourceStack(items = sources, formId = formId, unitId = unitId),
    status = status,
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Источник: пачка и её выделение в целых дозах. */
fun source(packageId: Uuid, doses: Int) = CourseSource(packageId, Doses(doses))
