package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses

/**
 * Назначение: чем лечатся и сколько — доза с единицей, форма, календарь и число доз. Собирается
 * из словаря, пачки для этого не нужно: лечение существует с момента, как врач его назвал, а
 * пачка потом подключается к тому, что назначено, и назначение решает, годится ли она (PLAN D5).
 *
 * Доза, форма и календарь после начала лечения неизменны — изменившееся лечение это отмена и
 * новый курс. Число доз правится: пропуски растягивают лечение, а закончить раньше человек может
 * рукой. Величина, и лежит она в двух местах сразу: у живого плана и у записи эпизода; менять её
 * нечем, поэтому в памяти они разойтись не могут, а согласованность при сохранении обеспечивает
 * транзакция (PLAN F5).
 */
data class Prescription(
    val dose: Dose,
    val form: DosageForm,
    val schedule: CourseSchedule,
    val totalDoses: Doses
) {
    init {
        require(!totalDoses.isNone) { "лечение без единой дозы — не лечение" }
    }

    /** Число доз меняется — назначение остаётся тем же лечением с другой длиной. */
    fun withTotalDoses(totalDoses: Doses): Prescription = copy(totalDoses = totalDoses)
}
