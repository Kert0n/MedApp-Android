package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Dose
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
    plannedPackage: PackageRef? = pack(id = PACK).ref,
    plannedAt: Instant = FIRST_PLANNED_AT,
    scheduledOn: LocalDate = FIRST_SCHEDULED_ON,
    scheduledTime: LocalTime = FIRST_SCHEDULED_TIME,
    plannedAmount: Dose = dose("2"),
) = CourseIntake(
    id = id,
    courseId = courseId,
    courseRevision = Revision(courseRevision),
    slot = ScheduledOccurrence(scheduledOn, scheduledTime, plannedAt),
    plannedAmount = plannedAmount,
    plannedPackage = plannedPackage
)

/** Внеплановый факт: курса нет, есть только состоявшийся приём. */
fun unplannedIntake(
    id: Uuid = INTAKE,
    taken: Package = pack(id = PACK),
    takenAmount: Dose = dose("1"),
    takenAt: Instant = LATER,
) = UnplannedIntake(
    id = id,
    dose = TakenDose(taken.ref, takenAmount, takenAt)
)
