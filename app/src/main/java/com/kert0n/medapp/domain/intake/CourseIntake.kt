package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.QuantityUnit
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Пункт курса вместе с ответом на него. План — [slot], [plannedAmount], [plannedPackageId] — ответ
 * не переписывает, переходы меняют только [answer]. [slot] вместе с [courseRevision] — тождество
 * пункта при повторной материализации (PLAN F4). [plannedPackageId] — пачка, из которой пункт
 * обеспечен, `null` у необеспеченного; фактическая пачка подтверждённого лежит в [TakenDose] и
 * может быть другой пачкой курса (D6).
 */
class CourseIntake(
    override val id: Uuid,
    val courseId: Uuid,
    val courseRevision: Revision,
    val slot: ScheduledOccurrence,
    val plannedAmount: Dose,
    val plannedPackageId: Uuid? = null,
    val answer: IntakeAnswer? = null
) : Intake {

    init {
        val taken = taken
        require(taken == null || taken.amount.unit == unit) {
            "фактическое количество измеряется единицей приёма"
        }
    }

    /**
     * Единица НА МОМЕНТ СОБЫТИЯ, и берётся она у плановой дозы: второе поле с той же единицей
     * могло бы с ней разойтись.
     */
    override val unit: QuantityUnit get() = plannedAmount.unit

    override val status: IntakeStatus
        get() = when (answer) {
            null -> IntakeStatus.PLANNED
            is IntakeAnswer.Taken -> IntakeStatus.TAKEN
            is IntakeAnswer.Skipped -> IntakeStatus.SKIPPED
            is IntakeAnswer.Missed -> IntakeStatus.MISSED
            is IntakeAnswer.Cancelled -> IntakeStatus.CANCELLED
        }

    override val taken: TakenDose? get() = (answer as? IntakeAnswer.Taken)?.dose

    /** Когда наступает пункт. */
    val plannedAt: Instant get() = slot.at

    /** Обеспечен ли пункт: источник с целой дозой под него найден (PLAN D5). */
    val isSupplied: Boolean get() = plannedPackageId != null

    /**
     * Подтверждение: фактические количество и пачка могут отличаться от плана, расход равен факту
     * (PLAN D5). Принимается сама пачка — аптечку и единицу события она приносит с собой. Подтверждается неотвеченный или пропущенный по времени пункт; повторное
     * подтверждение — второй факт со своим идентификатором, и здесь оно отвергается (E2).
     */
    fun confirm(pkg: Package, amount: Dose, at: Instant): CourseIntake {
        check(answer == null || answer is IntakeAnswer.Missed) {
            "подтверждается неотвеченный приём, а не $status"
        }
        return answered(IntakeAnswer.Taken(TakenDose(pkg, amount, at)))
    }

    /**
     * Человек отказался: расхода нет, потребность уменьшается. Повторный пропуск ничего не меняет;
     * отмены пропуска в первой версии нет.
     */
    fun skip(at: Instant): CourseIntake = respond(IntakeAnswer.Skipped(at))

    /** Ответа не было до конца календарного дня курса в его зоне (C1); подтвердить ещё можно. */
    fun miss(at: Instant): CourseIntake = respond(IntakeAnswer.Missed(at))

    /** Плановый пункт отменён вместе с курсом. Состоявшиеся приёмы этим не затрагиваются. */
    fun cancel(at: Instant): CourseIntake = respond(IntakeAnswer.Cancelled(at))

    private fun respond(to: IntakeAnswer): CourseIntake {
        if (answer != null && answer::class == to::class) return this
        check(answer == null) { "$to возможен для планового пункта, а не $status" }
        return answered(to)
    }

    /** Тот же пункт с новым ответом: план, тождество и редакция не меняются. */
    private fun answered(answer: IntakeAnswer): CourseIntake = CourseIntake(
        id = id,
        courseId = courseId,
        courseRevision = courseRevision,
        slot = slot,
        plannedAmount = plannedAmount,
        plannedPackageId = plannedPackageId,
        answer = answer
    )

    /** Тождество — [id]: подтверждённый приём остаётся тем же приёмом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is CourseIntake && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CourseIntake(id=$id, status=$status, courseId=$courseId)"
}
