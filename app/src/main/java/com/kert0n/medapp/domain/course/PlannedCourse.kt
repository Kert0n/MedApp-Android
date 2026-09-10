package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс с назначенным лечением: доза и расписание есть и неизменны. Состояний три — действующий,
 * завершённый, отменённый; завершённый и отменённый — история, и переходы это проверяют.
 */
class PlannedCourse(
    override val id: Uuid,
    override val title: String,
    override val note: String? = null,
    override val dose: Quantity,
    override val schedule: CourseSchedule,
    override val medicine: CourseMedicine,
    override val status: CourseStatus = CourseStatus.ACTIVE,
    override val revision: Revision = Revision.initial,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : Course {

    init {
        requireText(title, Course.TITLE_MAX_LENGTH, "Course.title")
        requireOptionalText(note, Course.NOTE_MAX_LENGTH, "Course.note")
        require(status != CourseStatus.DRAFT) { "у назначенного курса план уже есть" }
        require(dose.unitId == medicine.unitId) { "доза измеряется единицей источников курса" }
    }

    /** Название и заметка правятся и здесь: это не изменение назначенного лечения (PLAN D5). */
    fun rename(title: String, note: String?, at: Instant): PlannedCourse =
        changed(title = title, note = note, updatedAt = at)

    /** Пачки действующего курса менять можно: это не изменение дозы или календаря. */
    fun attach(pkg: Package, doses: Doses, at: Instant): Result<PlannedCourse> {
        if (!isActive) return Result.failure(CourseRejected(CourseRejected.Reason.COURSE_CLOSED))
        return medicine.attach(pkg, doses)
            .map { changed(medicine = it, revision = revision.next(), updatedAt = at) }
    }

    /**
     * Отвязка последней пачки форму и единицу не забывает: в них записаны доза и прошлые приёмы.
     * Курс просто становится необеспеченным (PLAN D5).
     */
    fun detach(packageId: Uuid, at: Instant): PlannedCourse {
        requireActive("отвязка источника")
        return changed(
            medicine = medicine.detach(packageId, forgetFormWhenEmpty = false),
            revision = revision.next(),
            updatedAt = at
        )
    }

    fun reorder(from: Int, to: Int, at: Instant): PlannedCourse {
        requireActive("порядок источников")
        val moved = medicine.reorder(from, to)
        if (moved == medicine) return this
        return changed(medicine = moved, revision = revision.next(), updatedAt = at)
    }

    fun allocate(packageId: Uuid, doses: Doses, at: Instant): PlannedCourse {
        requireActive("выделение")
        return changed(
            medicine = medicine.allocate(packageId, doses),
            revision = revision.next(),
            updatedAt = at
        )
    }

    /**
     * Обеспечение курса: на сколько из оставшихся приёмов хватит пачек препарата и с какого приёма
     * не хватает (PLAN D5). Дозу спрашивать не нужно — она у курса, и в этом его ценность.
     */
    fun coverage(
        remaining: List<ScheduledOccurrence>,
        availability: Availability
    ): CourseCoverage = medicine.coverage(dose, remaining, availability)

    /**
     * Верхняя граница ползунка пачки в целых дозах: меньшее из того, что пачка даёт, и того, что
     * потребность оставляет сверх выделенного остальным (PLAN D5).
     */
    fun maxDoses(packageId: Uuid, required: Doses, availability: Availability): Doses =
        medicine.maxDoses(packageId, dose, required, availability)

    /**
     * Курс с выделениями, зажатыми под нехватку и оставшуюся потребность. Доза, расписание и даты
     * не меняются — расписание это намерение человека, и чужое действие его не переписывает (C1).
     *
     * Зажимать нечего — возвращает себя: пересчёт идёт после каждого изменения входов (D5), и
     * поднимать редакцию на каждом было бы шумом в истории пунктов.
     */
    fun clamped(required: Doses, availability: Availability, at: Instant): PlannedCourse {
        requireActive("пересчёт выделения")
        val clamped = medicine.clampedTo(dose, required, availability)
        if (clamped == medicine) return this
        return changed(medicine = clamped, revision = revision.next(), updatedAt = at)
    }

    /**
     * Сколько целых доз остаётся выделено пачке после подтверждённого приёма: не больше
     * выделенного за вычетом расхода и не больше того, что в пачке осталось (PLAN D5).
     */
    fun dosesAfterIntake(packageId: Uuid, taken: Quantity, availableAfter: Quantity): Doses =
        medicine.dosesAfterIntake(packageId, dose, taken, availableAfter)

    /**
     * Раскладывает неотвеченные пункты этого курса, данные в календарном порядке, по пачкам
     * препарата: какой приём из какой пачки. `null` — приём не обеспечен, и полная доза
     * «неизвестно откуда» за него не записывается; пачки вне препарата не подставляются (PLAN D5).
     */
    fun assign(upcoming: List<CourseIntake>, availability: Availability): Map<Uuid, Uuid?> {
        upcoming.forEach { intake ->
            require(intake.courseId == id) { "пункт ${intake.id} не принадлежит курсу" }
            require(intake.status == IntakeStatus.PLANNED) {
                "раскладываются неотвеченные пункты, а не ${intake.status}"
            }
        }
        val order = medicine.spend(dose, Doses(upcoming.size), availability)
            .flatMap { (packageId, doses) -> List(doses.count) { packageId } }
        return upcoming.withIndex().associate { (index, intake) ->
            intake.id to order.getOrNull(index)
        }
    }

    /**
     * Календарь закончился и неотвеченных пунктов нет: выделения снимаются, пачки остаются — по
     * ним читается история приёмов (PLAN D5).
     */
    fun complete(at: Instant): PlannedCourse {
        check(isActive) { "завершается действующий курс, а не $status" }
        return changed(
            medicine = medicine.released(),
            status = CourseStatus.COMPLETED,
            updatedAt = at
        )
    }

    /**
     * Отмена, в том числе половина замены лечения: выделения снимаются, история остаётся. Будущие
     * неотвеченные пункты отменяет сценарий — у них свой переход (PLAN D5).
     */
    fun cancel(at: Instant): PlannedCourse {
        check(isActive) { "отменяется действующий курс, а не $status" }
        return changed(
            medicine = medicine.released(),
            status = CourseStatus.CANCELLED,
            updatedAt = at
        )
    }

    private val isActive: Boolean get() = status == CourseStatus.ACTIVE

    private fun requireActive(action: String) {
        check(isActive) { "$action недоступно для курса в состоянии $status" }
    }

    private fun changed(
        title: String = this.title,
        note: String? = this.note,
        medicine: CourseMedicine = this.medicine,
        status: CourseStatus = this.status,
        revision: Revision = this.revision,
        updatedAt: Instant = this.updatedAt
    ): PlannedCourse = PlannedCourse(
        id = id,
        title = title,
        note = note,
        dose = dose,
        schedule = schedule,
        medicine = medicine,
        status = status,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Тождество — [id]: переименованный курс остаётся тем же курсом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Course && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "PlannedCourse(id=$id, title=$title, status=$status)"
}
