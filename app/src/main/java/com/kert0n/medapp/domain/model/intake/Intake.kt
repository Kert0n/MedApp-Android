package com.kert0n.medapp.domain.model.intake

import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.uuid.Uuid

/**
 * Приём: один пункт плана вместе с ответом на него — либо внеплановый факт.
 *
 * **Сущность.** Подтверждение не делает приём другим приёмом: это тот же пункт, у которого
 * появился ответ. Тождество — [id], равенство по нему, состояние меняют только переходы.
 *
 * **Учёта расхода здесь нет.** Состояние учёта, связь с операцией очереди и с ручной сверкой
 * уехали в `IntakeSyncState` слоя данных: в локальной аптечке исходящих операций не существует
 * вовсе (PLAN E1), значит понятие есть только из-за сервера — тот же довод, что снял версию
 * предусловия с упаковки. Инвариант «у подтверждённого приёма расход не бывает
 * неприменим» — правило транзакции, где PLAN его и держит (F5).
 *
 * **Два поля источника вместо одного** (PLAN D6). Необеспеченному будущему приёму назначить
 * пачку нечего — свободного запаса под него нет; у подтверждённого пачка обязательна. Одно поле
 * пришлось бы либо сделать обязательным и врать про необеспеченный приём, либо необязательным и
 * потерять инвариант подтверждённого.
 *
 * [plannedPackageId] — точка расширения: когда понадобится «по субботам из дачной пачки»,
 * изменится способ его заполнения, а модель останется.
 *
 * [medKitId] и [unitId] пишутся **на момент события**: переименование пачки, смена единицы и
 * перенос в другую аптечку прошлые отчёты не переписывают.
 */
class Intake(
    val id: Uuid,
    val unitId: Uuid,                  // единица НА МОМЕНТ СОБЫТИЯ
    val status: IntakeStatus,
    val courseId: Uuid? = null,        // null — внеплановый разовый приём
    val courseRevision: Long? = null,  // какой редакцией расписания порождён
    val plannedPackageId: Uuid? = null, // null, когда приём НЕ ОБЕСПЕЧЕН
    val takenPackageId: Uuid? = null,  // null, пока не подтверждён
    val medKitId: Uuid? = null,        // аптечка НА МОМЕНТ СОБЫТИЯ
    val plannedAt: Instant? = null,
    val scheduledOn: LocalDate? = null, // исходная дата пункта в зоне курса
    val scheduledTime: LocalTime? = null, // исходное время, до разрешения перехода часов
    val plannedAmount: Quantity? = null,
    val takenAt: Instant? = null,
    val takenAmount: Quantity? = null,
    val respondedAt: Instant? = null
) {

    init {
        require(
            status != IntakeStatus.TAKEN ||
                (takenAt != null && takenAmount != null && takenPackageId != null)
        ) { "подтверждённый приём знает момент, количество и пачку" }
        require(status != IntakeStatus.PLANNED || plannedAt != null) {
            "плановый пункт знает, когда он наступает"
        }
        // Внеплановый приём бывает только состоявшимся: планировать разовый приём нечем — у него
        // нет ни курса, ни расписания, которое его породило.
        require(courseId != null || status == IntakeStatus.TAKEN) {
            "внеплановый приём существует только как факт"
        }
        // Пункт курса опознаётся редакцией и исходными датой со временем — это его тождество при
        // повторной материализации (PLAN F4), и без них строка не отличима от внепланового факта.
        if (courseId != null) {
            require(courseRevision != null && scheduledOn != null && scheduledTime != null) {
                "пункт курса называет редакцию и назначенные дату со временем"
            }
            require(plannedAt != null && plannedAmount != null) {
                "пункт курса знает плановый момент и плановую дозу"
            }
        } else {
            require(courseRevision == null && scheduledOn == null && scheduledTime == null) {
                "у внепланового приёма нет пункта расписания"
            }
        }
        require(plannedAmount == null || plannedAmount.unitId == unitId) {
            "плановая доза измеряется единицей приёма"
        }
        require(takenAmount == null || takenAmount.unitId == unitId) {
            "фактическое количество измеряется единицей приёма"
        }
        require(takenAmount == null || !takenAmount.isZero) {
            "принятый ноль — это пропуск, а не приём"
        }
    }

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
     * и остаток заново. Повторное подтверждение уже подтверждённого приёма отвергается — второе
     * подтверждение это второй факт со своим идентификатором, а не тот же самый (PLAN E2).
     */
    fun confirm(packageId: Uuid, medKitId: Uuid, amount: Quantity, at: Instant): Intake {
        check(status == IntakeStatus.PLANNED || status == IntakeStatus.MISSED) {
            "подтверждается неотвеченный приём, а не $status"
        }
        return changed(
            status = IntakeStatus.TAKEN,
            takenPackageId = packageId,
            medKitId = medKitId,
            takenAmount = amount,
            takenAt = at,
            respondedAt = at
        )
    }

    /**
     * Человек отказался от приёма. Расхода нет: потребность уменьшается, а лишнее выделение
     * снимается с конца стека (PLAN D5).
     *
     * Идемпотентно для самого пропуска и отвергает всё остальное: подтверждение пропущенного —
     * это отмена пропуска, отдельное явное действие, которого в первой версии нет.
     */
    fun skip(at: Instant): Intake = answered(IntakeStatus.SKIPPED, at)

    /**
     * Ответа не было до конца календарного дня курса в его зоне (PLAN C1).
     *
     * Не расход и не отказ: пропущенный пункт ещё может быть подтверждён позже.
     */
    fun miss(at: Instant): Intake = answered(IntakeStatus.MISSED, at)

    /** Плановый пункт отменён вместе с курсом. Состоявшиеся приёмы этим не затрагиваются. */
    fun cancel(at: Instant): Intake = answered(IntakeStatus.CANCELLED, at)

    private fun answered(to: IntakeStatus, at: Instant): Intake {
        if (status == to) return this
        check(status == IntakeStatus.PLANNED) { "$to возможен для планового пункта, а не $status" }
        return changed(status = to, respondedAt = at)
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * Плановая часть в списке отсутствует целиком: пункт порождён редакцией расписания, и ответ
     * на него не переписывает ни назначенное время, ни плановую дозу, ни плановую пачку.
     */
    private fun changed(
        status: IntakeStatus = this.status,
        takenPackageId: Uuid? = this.takenPackageId,
        medKitId: Uuid? = this.medKitId,
        takenAmount: Quantity? = this.takenAmount,
        takenAt: Instant? = this.takenAt,
        respondedAt: Instant? = this.respondedAt
    ): Intake = Intake(
        id = id,
        unitId = unitId,
        status = status,
        courseId = courseId,
        courseRevision = courseRevision,
        plannedPackageId = plannedPackageId,
        takenPackageId = takenPackageId,
        medKitId = medKitId,
        plannedAt = plannedAt,
        scheduledOn = scheduledOn,
        scheduledTime = scheduledTime,
        plannedAmount = plannedAmount,
        takenAt = takenAt,
        takenAmount = takenAmount,
        respondedAt = respondedAt
    )

    /** Тождество — [id]: подтверждённый приём остаётся тем же приёмом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Intake && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Intake(id=$id, status=$status, courseId=$courseId)"
}
