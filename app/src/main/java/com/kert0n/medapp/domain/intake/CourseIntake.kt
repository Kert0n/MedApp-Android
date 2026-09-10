package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Пункт курса вместе с ответом на него.
 *
 * **Два поля источника вместо одного** (PLAN D6). Необеспеченному будущему пункту назначить пачку
 * нечего — свободного запаса под него нет; у подтверждённого пачка обязательна и лежит в
 * [TakenDose]. Одно поле пришлось бы либо сделать обязательным и врать про необеспеченный пункт,
 * либо необязательным и потерять инвариант подтверждённого.
 *
 * [plannedPackageId] — точка расширения: когда понадобится «по субботам из дачной пачки»,
 * изменится способ его заполнения, а модель останется.
 *
 * [slot] — тот самый пункт расписания, из которого пункт порождён: исходные дата и время плюс
 * разрешённый момент. Вместе с [courseRevision] это его тождество при повторной материализации
 * (PLAN F4), и хранится он тем же типом, каким расписание его и выдало.
 *
 * [answer] — единственное изменяемое переходами: план ответ не переписывает.
 */
class CourseIntake(
    override val id: Uuid,
    val courseId: Uuid,
    val courseRevision: Revision,
    val slot: ScheduledOccurrence,
    val plannedAmount: Quantity,
    val plannedPackageId: Uuid? = null,
    val answer: IntakeAnswer? = null
) : Intake {

    init {
        val taken = taken
        require(taken == null || taken.amount.unitId == unitId) {
            "фактическое количество измеряется единицей приёма"
        }
    }

    /**
     * Единица НА МОМЕНТ СОБЫТИЯ, и берётся она у плановой дозы: второе поле с той же единицей
     * могло бы с ней разойтись.
     */
    override val unitId: Uuid get() = plannedAmount.unitId

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
     * Подтверждение приёма.
     *
     * Фактическое количество может отличаться от планового: будущие пункты от этого не меняются,
     * расход равен факту, а обеспечение пересчитывается (PLAN D5). Пачка называется явно — она
     * могла оказаться другой из источников курса, и тогда расход относится к фактической.
     *
     * Из [IntakeStatus.MISSED] подтверждение разрешено: поздний ответ проверяет текущий источник
     * и остаток заново. Повторное подтверждение уже подтверждённого отвергается — второе
     * подтверждение это второй факт со своим идентификатором, а не тот же самый (PLAN E2).
     */
    fun confirm(packageId: Uuid, medKitId: Uuid, amount: Quantity, at: Instant): CourseIntake {
        check(answer == null || answer is IntakeAnswer.Missed) {
            "подтверждается неотвеченный приём, а не $status"
        }
        return answered(IntakeAnswer.Taken(TakenDose(packageId, medKitId, amount, at)))
    }

    /**
     * Человек отказался от приёма. Расхода нет: потребность уменьшается, а лишнее выделение
     * снимается с конца стека (PLAN D5).
     *
     * Идемпотентно для самого пропуска и отвергает всё остальное: подтверждение пропущенного —
     * это отмена пропуска, отдельное явное действие, которого в первой версии нет.
     */
    fun skip(at: Instant): CourseIntake = respond(IntakeAnswer.Skipped(at))

    /**
     * Ответа не было до конца календарного дня курса в его зоне (PLAN C1).
     *
     * Не расход и не отказ: пропущенный пункт ещё может быть подтверждён позже.
     */
    fun miss(at: Instant): CourseIntake = respond(IntakeAnswer.Missed(at))

    /** Плановый пункт отменён вместе с курсом. Состоявшиеся приёмы этим не затрагиваются. */
    fun cancel(at: Instant): CourseIntake = respond(IntakeAnswer.Cancelled(at))

    private fun respond(to: IntakeAnswer): CourseIntake {
        if (answer != null && answer::class == to::class) return this
        check(answer == null) { "$to возможен для планового пункта, а не $status" }
        return answered(to)
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * Плановая часть в него не входит вовсе: пункт порождён редакцией расписания, и ответ на него
     * не переписывает ни назначенное время, ни плановую дозу, ни плановую пачку.
     */
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
