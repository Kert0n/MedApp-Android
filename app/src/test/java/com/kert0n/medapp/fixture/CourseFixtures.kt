package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.course.CourseSchedule
import com.kert0n.medapp.domain.model.course.CourseStatus
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

/** Курс: тест называет только то, что проверяет. По умолчанию — черновик с одним названием. */
fun course(
    id: Uuid = COURSE,
    title: String = "Парацетамол, пять дней",
    note: String? = null,
    doseAmount: BigDecimal? = null,
    unitId: Uuid? = null,
    formId: Uuid? = null,
    schedule: CourseSchedule? = null,
    status: CourseStatus = CourseStatus.DRAFT,
    revision: Long = 0,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = Course(
    id = id,
    title = title,
    note = note,
    doseAmount = doseAmount,
    unitId = unitId,
    formId = formId,
    schedule = schedule,
    status = status,
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt
)
