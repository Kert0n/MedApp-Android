package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс с назначенным лечением: доза и расписание есть и неизменны.
 *
 * Именно этот тип принимают вычислители обеспечения, выделения и прогноза: `requireNotNull` про
 * дозу им больше не нужен — курса без дозы к ним не приходит.
 *
 * Состояний три, и это состояния **жизни** курса, а не наличия плана: действующий, завершённый и
 * отменённый. Завершённый и отменённый — история: менять в них нечего, и переходы это проверяют
 * (как две оси у упаковки, PLAN D3).
 */
class PlannedCourse(
    override val id: Uuid,
    override val title: String,
    override val note: String? = null,
    override val dose: Quantity,
    override val schedule: CourseSchedule,
    override val stack: SourceStack,
    override val status: CourseStatus = CourseStatus.ACTIVE,
    override val revision: Long = 0,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : Course {

    init {
        requireText(title, COURSE_TITLE_MAX_LENGTH, "Course.title")
        requireOptionalText(note, COURSE_NOTE_MAX_LENGTH, "Course.note")
        require(revision >= 0) { "редакция курса не бывает отрицательной" }
        require(status != CourseStatus.DRAFT) { "у назначенного курса план уже есть" }
        require(dose.unitId == stack.unitId) { "доза измеряется единицей источников курса" }
    }

    /** Название и заметка правятся и здесь: это не изменение назначенного лечения (PLAN D5). */
    fun rename(title: String, note: String?, at: Instant): PlannedCourse =
        changed(title = title, note = note, updatedAt = at)

    /** Источники действующего курса менять можно — это не изменение дозы или календаря. */
    fun attach(pkg: Package, doses: Doses, at: Instant): Result<PlannedCourse> {
        if (!isActive) return Result.failure(CourseRejected(CourseRejection.COURSE_CLOSED))
        return stack.attach(pkg, doses)
            .map { changed(stack = it, revision = revision + 1, updatedAt = at) }
    }

    /**
     * Отвязка последнего источника форму и единицу **не сбрасывает**: иначе доза и расписание
     * мгновенно потеряли бы смысл, а состоявшиеся приёмы остались бы с единицей, которой у курса
     * больше нет. Курс просто становится необеспеченным (PLAN D5).
     */
    fun detach(packageId: Uuid, at: Instant): PlannedCourse {
        requireActive("отвязка источника")
        return changed(
            stack = stack.detach(packageId, forgetFormWhenEmpty = false),
            revision = revision + 1,
            updatedAt = at
        )
    }

    fun reorder(from: Int, to: Int, at: Instant): PlannedCourse {
        requireActive("порядок источников")
        val moved = stack.reorder(from, to)
        if (moved == stack) return this
        return changed(stack = moved, revision = revision + 1, updatedAt = at)
    }

    fun allocate(packageId: Uuid, doses: Doses, at: Instant): PlannedCourse {
        requireActive("выделение")
        return changed(
            stack = stack.allocate(packageId, doses),
            revision = revision + 1,
            updatedAt = at
        )
    }

    /**
     * Календарь закончился и неотвеченных пунктов не осталось.
     *
     * Выделения обнуляются: оставшегося выделения у закончившегося курса нет, и это то же
     * событие, что снятие брони и освобождение назначений (PLAN D5). Сам стек остаётся —
     * история приёмов читается по нему; редакция не растёт, прошлые пункты не пересоздаются.
     */
    fun complete(at: Instant): PlannedCourse {
        check(isActive) { "завершается действующий курс, а не $status" }
        return changed(stack = stack.released(), status = CourseStatus.COMPLETED, updatedAt = at)
    }

    /**
     * Отмена — в том числе половина замены лечения: старый курс отменяется с сохранением
     * истории, новый создаётся отдельным черновиком (PLAN D5).
     *
     * Состоявшиеся приёмы не переписываются; будущие неотвеченные пункты отменяет сценарий, а не
     * модель курса — они отдельные записи со своим переходом.
     */
    fun cancel(at: Instant): PlannedCourse {
        check(isActive) { "отменяется действующий курс, а не $status" }
        return changed(stack = stack.released(), status = CourseStatus.CANCELLED, updatedAt = at)
    }

    private val isActive: Boolean get() = status == CourseStatus.ACTIVE

    private fun requireActive(action: String) {
        check(isActive) { "$action недоступно для курса в состоянии $status" }
    }

    private fun changed(
        title: String = this.title,
        note: String? = this.note,
        stack: SourceStack = this.stack,
        status: CourseStatus = this.status,
        revision: Long = this.revision,
        updatedAt: Instant = this.updatedAt
    ): PlannedCourse = PlannedCourse(
        id = id,
        title = title,
        note = note,
        dose = dose,
        schedule = schedule,
        stack = stack,
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
