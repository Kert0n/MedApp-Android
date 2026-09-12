package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.QuantityUnit
import kotlin.uuid.Uuid

/**
 * Приём глазами экрана — величина без переходов (PLAN H1). Случая два, как и у самого приёма:
 * пункт курса с ответом на него и внеплановый факт; строит проекцию приём ([Intake.projection]).
 */
sealed interface IntakeProjection {

    val id: Uuid

    val unit: QuantityUnit

    val status: IntakeStatus

    val taken: TakenDose?

    data class Scheduled(
        override val id: Uuid,
        val courseId: Uuid,
        val courseRevision: Revision,
        val slot: ScheduledOccurrence,
        val plannedAmount: Dose,
        val plannedPackage: PackageRef?,
        val answer: IntakeAnswer?,
        override val status: IntakeStatus,
        override val taken: TakenDose?
    ) : IntakeProjection {
        override val unit: QuantityUnit get() = plannedAmount.unit
    }

    data class Unplanned(
        override val id: Uuid,
        val dose: TakenDose
    ) : IntakeProjection {
        override val unit: QuantityUnit get() = dose.amount.unit
        override val status: IntakeStatus get() = IntakeStatus.TAKEN
        override val taken: TakenDose get() = dose
    }
}
