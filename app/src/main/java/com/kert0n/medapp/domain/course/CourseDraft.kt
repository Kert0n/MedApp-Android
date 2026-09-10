package com.kert0n.medapp.domain.course

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
 * Черновик курса: лечение, которое ещё собирают.
 *
 * `DRAFT` с одним названием и заметкой — законное сохраняемое состояние, а не полуфабрикат
 * (PLAN D5). Броней у черновика нет и упаковку он не занимает: подключённые источники —
 * предварительный выбор.
 *
 * **Доза здесь — число без единицы, и это честно.** «Две штуки чего-то» человек записывает до
 * того, как выбрал пачку, а единицу задаёт первый источник. Величиной доза становится ровно
 * тогда, когда известны оба, — при активации.
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
     * Название и заметка правятся в любом состоянии: это не изменение назначенного лечения.
     *
     * [revision] при этом не растёт. Редакция отмечает изменение расписания и источников, и
     * приёмы связаны с ней через `CourseIntake.courseRevision`; поднимать её на переименовании
     * значило бы объявлять уже материализованные пункты устаревшими из-за исправленной опечатки.
     */
    fun rename(title: String, note: String?, at: Instant): CourseDraft =
        changed(title = title, note = note, updatedAt = at)

    /**
     * Доза задаётся только у черновика.
     *
     * После активации доза, единица, форма и расписание неизменны: изменившееся лечение — это
     * отмена прежнего курса с сохранением истории и создание нового (PLAN D5). Иначе прошлые
     * приёмы остались бы записанными в дозе, которой у курса больше нет. Проверять состояние не
     * нужно: у назначенного курса этого перехода нет вовсе.
     */
    fun setDose(amount: BigDecimal, at: Instant): CourseDraft =
        changed(doseAmount = amount, revision = revision.next(), updatedAt = at)

    /**
     * Расписание задаётся только у черновика — по той же причине, что и доза.
     *
     * Редакция растёт: расписание меняет состав будущих пунктов, и приёмы связаны с ней через
     * `CourseIntake.courseRevision`.
     */
    fun setSchedule(schedule: CourseSchedule, at: Instant): CourseDraft =
        changed(schedule = schedule, revision = revision.next(), updatedAt = at)

    /** Подключает пачку последней в стеке — самой низкой по приоритету расходования. */
    fun attach(pkg: Package, doses: Doses, at: Instant): Result<CourseDraft> =
        medicine.attach(pkg, doses)
            .map { changed(medicine = it, revision = revision.next(), updatedAt = at) }

    /**
     * Отвязка последнего источника у черновика **сбрасывает** форму и единицу: там ещё нечего
     * терять (PLAN D5).
     */
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
     * Активация: с этого момента доза, единица, форма и расписание неизменны, а выделения
     * становятся бронями и занимают пачки (PLAN D5, F1).
     *
     * Требует расписания, дозы и хотя бы одного источника — и это единственное место, где они
     * проверяются: дальше их наличие обеспечивает тип.
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

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * [id] и [createdAt] в списке отсутствуют: тождество и момент начала курса не меняются.
     */
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
