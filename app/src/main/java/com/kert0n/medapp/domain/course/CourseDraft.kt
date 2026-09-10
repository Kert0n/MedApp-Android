package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.requireNonNegativeDecimal
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Черновик курса — законное сохраняемое состояние: названия уже достаточно (PLAN D5). Доза здесь —
 * число без единицы: единицу задаёт первая пачка препарата, и величиной доза становится при
 * активации. Броней у черновика нет: выбранные пачки — предварительный выбор.
 */
class CourseDraft(
    override val id: Uuid,
    override val title: String,
    override val note: String? = null,
    val doseAmount: BigDecimal? = null,
    override val schedule: CourseSchedule? = null,
    override val medicine: CourseMedicine = CourseMedicine(),
    override val revision: Revision = Revision.initial,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : Course {

    init {
        requireText(title, Course.TITLE_MAX_LENGTH, "Course.title")
        requireOptionalText(note, Course.NOTE_MAX_LENGTH, "Course.note")
        doseAmount?.let { amount ->
            requireNonNegativeDecimal(
                amount = amount,
                field = "доза курса",
                maxScale = Quantity.SCALE,
                maxIntegerDigits = Quantity.MAX_INTEGER_DIGITS
            )
            // Нулевая доза — не лечение, а деление на ноль в обеспечении: `dosesIn` на ней бросает.
            require(amount.signum() > 0) { "разовая доза курса строго положительна" }
        }
    }

    override val status: CourseStatus get() = CourseStatus.DRAFT

    override val dose: Quantity?
        get() = if (doseAmount != null && unitId != null) Quantity(doseAmount, unitId!!) else null

    /**
     * Название и заметка правятся без роста редакции: редакция отмечает изменение будущих пунктов,
     * а исправленная опечатка их не меняет.
     */
    fun rename(title: String, note: String?, at: Instant): CourseDraft =
        changed(title = title, note = note, updatedAt = at)

    /**
     * Доза задаётся только у черновика: у назначенного курса она неизменна, а другое лечение —
     * это отмена курса и новый черновик (PLAN D5).
     */
    fun setDose(amount: BigDecimal, at: Instant): CourseDraft =
        changed(doseAmount = amount, revision = revision.next(), updatedAt = at)

    /**
     * Расписание задаётся только у черновика, как и доза; редакция растёт, потому что меняется
     * состав будущих пунктов.
     */
    fun setSchedule(schedule: CourseSchedule, at: Instant): CourseDraft =
        changed(schedule = schedule, revision = revision.next(), updatedAt = at)

    /** Подключает пачку к препарату последней в очереди расходования. */
    fun attach(pkg: Package, doses: Doses, at: Instant): Result<CourseDraft> =
        medicine.attach(pkg, doses)
            .map { changed(medicine = it, revision = revision.next(), updatedAt = at) }

    /** Отвязка последней пачки у черновика забывает форму и единицу: терять ещё нечего. */
    fun detach(packageId: Uuid, at: Instant): CourseDraft = changed(
        medicine = medicine.detach(packageId, forgetFormWhenEmpty = true),
        revision = revision.next(),
        updatedAt = at
    )

    fun reorder(from: Int, to: Int, at: Instant): CourseDraft {
        val moved = medicine.reorder(from, to)
        if (moved == medicine) return this
        return changed(medicine = moved, revision = revision.next(), updatedAt = at)
    }

    fun allocate(packageId: Uuid, doses: Doses, at: Instant): CourseDraft = changed(
        medicine = medicine.allocate(packageId, doses),
        revision = revision.next(),
        updatedAt = at
    )

    /**
     * Верхняя граница ползунка пачки. Пока доза не задана, границы нет: выделять нечего, и ноль
     * здесь честнее выдуманного числа.
     */
    fun maxDoses(packageId: Uuid, required: Doses, availability: Availability): Doses {
        val dose = dose ?: return Doses.none
        return medicine.maxDoses(packageId, dose, required, availability)
    }

    /**
     * Активация: нужны расписание, доза и хотя бы одна пачка; дальше их наличие обеспечивает тип
     * [PlannedCourse], а выделения становятся бронями (PLAN D5, F1).
     */
    fun activate(at: Instant): Result<PlannedCourse> {
        val schedule = schedule ?: return rejected(CourseRejected.Reason.SCHEDULE_MISSING)
        val dose = dose ?: return rejected(CourseRejected.Reason.DOSE_MISSING)
        if (medicine.isEmpty) return rejected(CourseRejected.Reason.SOURCES_MISSING)
        return Result.success(
            PlannedCourse(
                id = id,
                title = title,
                note = note,
                dose = dose,
                schedule = schedule,
                medicine = medicine,
                status = CourseStatus.ACTIVE,
                revision = revision,
                createdAt = createdAt,
                updatedAt = at
            )
        )
    }

    private fun rejected(reason: CourseRejected.Reason): Result<PlannedCourse> =
        Result.failure(CourseRejected(reason))

    /** Изменённый экземпляр; [id] и [createdAt] не меняются. */
    private fun changed(
        title: String = this.title,
        note: String? = this.note,
        doseAmount: BigDecimal? = this.doseAmount,
        schedule: CourseSchedule? = this.schedule,
        medicine: CourseMedicine = this.medicine,
        revision: Revision = this.revision,
        updatedAt: Instant = this.updatedAt
    ): CourseDraft = CourseDraft(
        id = id,
        title = title,
        note = note,
        doseAmount = doseAmount,
        schedule = schedule,
        medicine = medicine,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Тождество — [id]: переименованный курс остаётся тем же курсом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Course && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CourseDraft(id=$id, title=$title)"
}
