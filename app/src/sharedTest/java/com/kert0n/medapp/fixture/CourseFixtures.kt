package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseMedicine
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.course.CourseSchedule
import com.kert0n.medapp.domain.course.CourseSource
import com.kert0n.medapp.domain.course.Prescription
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
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
    dose: Dose? = null,
    form: DosageForm? = null,
    schedule: CourseSchedule? = null,
    totalDoses: Int? = null,
    sources: List<CourseSource> = emptyList(),
    revision: Long = 0,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = CourseDraft(
    id = id,
    title = title,
    note = note,
    dose = dose,
    form = form,
    schedule = schedule,
    totalDoses = totalDoses?.let(::Doses),
    medicine = CourseMedicine(sources = sources),
    revision = Revision(revision),
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Черновик с назначенными дозой и формой — к нему уже можно подключать пачки. */
fun prescribedDraft(
    dose: Dose = dose("2"),
    form: DosageForm = TABLET_FORM,
    schedule: CourseSchedule? = null,
    totalDoses: Int? = null,
    sources: List<CourseSource> = emptyList()
) = course(dose = dose, form = form, schedule = schedule, totalDoses = totalDoses, sources = sources)

/** Назначение: две таблетки раз в день, семь доз — неделя, если тест не сказал иначе. */
fun prescription(
    doseAmount: BigDecimal = BigDecimal("2"),
    unit: QuantityUnit = TABLETS,
    form: DosageForm = TABLET_FORM,
    schedule: CourseSchedule = schedule(),
    totalDoses: Int = 7
) = Prescription(
    dose = Dose(Quantity(doseAmount, unit)),
    form = form,
    schedule = schedule,
    totalDoses = Doses(totalDoses)
)

/**
 * Действующий план: доза, единица, форма, число доз и расписание у него есть по типу, и называть
 * их в каждом тесте незачем.
 */
fun activeCourse(
    id: Uuid = COURSE,
    doseAmount: BigDecimal = BigDecimal("2"),
    unit: QuantityUnit = TABLETS,
    form: DosageForm = TABLET_FORM,
    schedule: CourseSchedule = schedule(),
    totalDoses: Int = 7,
    sources: List<CourseSource> = emptyList(),
    revision: Long = 1,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = Course(
    id = id,
    prescription = prescription(doseAmount, unit, form, schedule, totalDoses),
    medicine = CourseMedicine(sources = sources),
    revision = Revision(revision),
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Запись эпизода: по умолчанию открытая — лечение идёт, план для него ещё существует. */
fun courseRecord(
    id: Uuid = COURSE,
    title: String = "Парацетамол, пять дней",
    note: String? = null,
    prescription: Prescription = prescription(),
    startedAt: Instant = EARLIER,
    outcome: CourseRecord.Outcome? = null,
    closedAt: Instant? = null
) = CourseRecord(
    id = id,
    title = title,
    note = note,
    prescription = prescription,
    startedAt = startedAt,
    outcome = outcome,
    closedAt = closedAt
)

/** Источник: пачка и её выделение в целых дозах. */
fun source(packageId: Uuid, doses: Int) = CourseSource(packageId, Doses(doses))

/** Препарат курса: пачки в порядке расходования, каждая со своим выделением. */
fun medicine(vararg sources: CourseSource) = CourseMedicine(sources = sources.toList())
