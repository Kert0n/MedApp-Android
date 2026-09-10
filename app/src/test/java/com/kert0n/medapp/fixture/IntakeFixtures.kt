package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.calc.schedule.ScheduledOccurrence
import com.kert0n.medapp.domain.model.intake.CourseIntake
import com.kert0n.medapp.domain.model.intake.Intake
import com.kert0n.medapp.domain.model.intake.IntakeStatus
import com.kert0n.medapp.domain.model.intake.TakenDose
import com.kert0n.medapp.domain.model.intake.UnplannedIntake
import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.uuid.Uuid

val INTAKE: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000061")

/** Второй приём: у каждого подтверждения свой идентификатор (PLAN E2). */
val OTHER_INTAKE: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000062")

/** Первый пункт недельного расписания фикстуры: девять утра первого дня в зоне курса. */
val FIRST_SCHEDULED_ON: LocalDate = schedule().start

val FIRST_SCHEDULED_TIME: LocalTime = LocalTime.of(9, 0)

val FIRST_PLANNED_AT: Instant =
    ZonedDateTime.of(FIRST_SCHEDULED_ON, FIRST_SCHEDULED_TIME, MOSCOW).toInstant()

/**
 * Плановый пункт курса: обеспеченный, с плановой дозой в две таблетки.
 *
 * `plannedPackageId = null` называется тестом явно, когда проверяется необеспеченный приём.
 */
fun plannedIntake(
    id: Uuid = INTAKE,
    courseId: Uuid = COURSE,
    courseRevision: Long = 1,
    plannedPackageId: Uuid? = PACK,
    plannedAt: Instant = FIRST_PLANNED_AT,
    scheduledOn: LocalDate = FIRST_SCHEDULED_ON,
    scheduledTime: LocalTime = FIRST_SCHEDULED_TIME,
    plannedAmount: Quantity = tablets("2"),
    unitId: Uuid = TABLETS
) = CourseIntake(
    id = id,
    unitId = unitId,
    courseId = courseId,
    courseRevision = courseRevision,
    slot = ScheduledOccurrence(scheduledOn, scheduledTime, plannedAt),
    plannedAmount = plannedAmount,
    plannedPackageId = plannedPackageId
)

/** Внеплановый факт: курса нет, есть только состоявшийся приём. */
fun unplannedIntake(
    id: Uuid = INTAKE,
    takenPackageId: Uuid = PACK,
    medKitId: Uuid = HOME_KIT,
    takenAmount: Quantity = tablets("1"),
    takenAt: Instant = LATER,
    unitId: Uuid = TABLETS
) = UnplannedIntake(
    id = id,
    unitId = unitId,
    dose = TakenDose(takenPackageId, medKitId, takenAmount, takenAt)
)
