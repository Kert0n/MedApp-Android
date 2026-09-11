package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.doses
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
    val id: Uuid,
    val title: String,
    val note: String? = null,
    val doseAmount: BigDecimal? = null,
    val schedule: CourseSchedule? = null,
    val medicine: CourseMedicine = CourseMedicine(),
    val revision: Revision = Revision.initial,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    init {
        requireText(title, CourseRecord.TITLE_MAX_LENGTH, "CourseDraft.title")
        requireOptionalText(note, CourseRecord.NOTE_MAX_LENGTH, "CourseDraft.note")
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

    /**
     * Доза как величина — только когда первая пачка принесла единицу. Число человек называет сам,
     * единицу приносит препарат, и порядок бывает любым: «две штуки чего-то» и «пачка выбрана» —
     * оба законные состояния черновика.
     */
    val dose: Dose?
        get() {
            val unitId = medicine.unitId ?: return null
            return doseAmount?.let { Dose(Quantity(it, unitId)) }
        }

    val sources: List<CourseSource> get() = medicine.sources

    val formId: Uuid? get() = medicine.formId

    val unitId: Uuid? get() = medicine.unitId

    val allocatedDosesTotal: Doses get() = medicine.allocatedTotal

    /** Выделение пачки в единицах пачки; `null` — пачка не выбрана или доза ещё не задана. */
    fun allocatedOf(pkg: Package): Quantity? {
        val allocated = medicine.allocatedTo(pkg.id) ?: return null
        return dose?.times(allocated)
    }

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

    /**
     * Отвязка последней пачки форму и единицу не забывает: препарат назначения выбран, и
     * заменить его другим — это другое лечение, а не правка этого черновика.
     */
    fun detach(pkg: Package, at: Instant): CourseDraft = changed(
        medicine = medicine.detach(pkg.id),
        revision = revision.next(),
        updatedAt = at
    )

    fun reorder(from: Int, to: Int, at: Instant): CourseDraft {
        val moved = medicine.reorder(from, to)
        if (moved == medicine) return this
        return changed(medicine = moved, revision = revision.next(), updatedAt = at)
    }

    fun allocate(pkg: Package, doses: Doses, at: Instant): CourseDraft = changed(
        medicine = medicine.allocate(pkg.id, doses),
        revision = revision.next(),
        updatedAt = at
    )

    /**
     * Верхняя граница ползунка пачки. Пока доза не задана, границы нет: выделять нечего, и ноль
     * здесь честнее выдуманного числа.
     */
    fun maxDoses(pkg: Package, required: Doses, availability: Availability): Doses {
        val dose = dose ?: return 0.doses
        return medicine.maxDoses(pkg.id, dose, required, availability)
    }

    /**
     * Активация: нужны расписание, доза и хотя бы одна пачка. Дальше их наличие обеспечивает тип
     * [Course], а выделения становятся бронями (PLAN D5, F1).
     *
     * Рождаются **двое**: план, которым пользуются, и запись, которая останется, когда план
     * уничтожится. Возвращаются они вместе, поэтому завести эпизод без записи невозможно — а
     * значит, аналитике не придётся собирать историю из идущих курсов и закрытых по отдельности.
     */
    fun activate(at: Instant): Result<Activation> {
        val schedule = schedule ?: return rejected(CourseRejected.Reason.SCHEDULE_MISSING)
        val dose = dose ?: return rejected(CourseRejected.Reason.DOSE_MISSING)
        if (medicine.isEmpty) return rejected(CourseRejected.Reason.SOURCES_MISSING)
        val prescription = Prescription(dose = dose, schedule = schedule)
        return Result.success(
            Activation(
                course = Course(
                    id = id,
                    prescription = prescription,
                    medicine = medicine,
                    revision = revision,
                    createdAt = createdAt,
                    updatedAt = at
                ),
                record = CourseRecord(
                    id = id,
                    title = title,
                    note = note,
                    prescription = prescription,
                    startedAt = at
                )
            )
        )
    }

    private fun rejected(reason: CourseRejected.Reason): Result<Activation> =
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

    /** Тождество — [id]: переименованный черновик остаётся тем же черновиком. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is CourseDraft && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CourseDraft(id=$id, title=$title)"

    /**
     * Начатое лечение: план и запись одного эпизода, с общим [Course.id] и одним назначением.
     * Один тип на двоих потому, что порознь они не рождаются.
     */
    class Activation(val course: Course, val record: CourseRecord)
}
