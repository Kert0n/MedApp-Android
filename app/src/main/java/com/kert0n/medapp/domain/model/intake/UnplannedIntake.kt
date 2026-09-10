package com.kert0n.medapp.domain.model.intake

import kotlin.uuid.Uuid

/**
 * Внеплановый приём — разовый факт вне курса.
 *
 * Он бывает только состоявшимся: планировать разовый приём нечем, у него нет ни курса, ни
 * расписания, которое его породило. Раньше это утверждало `require`, теперь — тип: полей плана
 * здесь просто нет, а [status] отвечать иначе, чем [IntakeStatus.TAKEN], не умеет.
 */
class UnplannedIntake(
    override val id: Uuid,
    override val unitId: Uuid,
    val dose: TakenDose
) : Intake {

    init {
        require(dose.amount.unitId == unitId) { "фактическое количество измеряется единицей приёма" }
    }

    override val status: IntakeStatus get() = IntakeStatus.TAKEN

    override val taken: TakenDose get() = dose

    /** Тождество — [id]: запись остаётся той же записью. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is UnplannedIntake && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "UnplannedIntake(id=$id, at=${dose.at})"
}
