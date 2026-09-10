package com.kert0n.medapp.domain.model.course

import com.kert0n.medapp.domain.model.value.QUANTITY_MAX_INTEGER_DIGITS
import com.kert0n.medapp.domain.model.value.QUANTITY_SCALE
import com.kert0n.medapp.domain.model.value.Quantity
import com.kert0n.medapp.domain.model.value.requireNonNegativeDecimal
import com.kert0n.medapp.domain.model.value.requireOptionalText
import com.kert0n.medapp.domain.model.value.requireText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс — лечение, которое человек себе назначил: сколько принимать, по какому календарю и из
 * каких пачек.
 *
 * **Курс начинается заметкой, а не расписанием.** Черновик с одним названием — законное
 * сохраняемое состояние, а не полуфабрикат: сценарий «записал у врача → купил → внёс» начинается
 * раньше, чем известны даты и пачки (PLAN D5). Поэтому ни расписания, ни дозы, ни источников у
 * него может не быть.
 *
 * **Сущность:** переименованный курс — тот же курс, и приёмы, уже порождённые им, остаются его
 * приёмами. Тождество — [id], равенство по нему.
 *
 * Курс на сервер не уезжает вовсе (PLAN C0): расписаний, приёмов и курсов там нет и не будет,
 * поэтому обвязки синхронизации у него нет по построению, а не по решению.
 */
class Course(
    val id: Uuid,
    val title: String,
    val note: String? = null,            // «что купить», запись от врача
    val doseAmount: BigDecimal? = null,  // разовая доза курса
    val unitId: Uuid? = null,            // фиксируется первым источником
    val formId: Uuid? = null,            // фиксируется первым источником
    val schedule: CourseSchedule? = null,
    val status: CourseStatus = CourseStatus.DRAFT,
    val revision: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    init {
        requireText(title, COURSE_TITLE_MAX_LENGTH, "Course.title")
        requireOptionalText(note, COURSE_NOTE_MAX_LENGTH, "Course.note")
        require(revision >= 0) { "редакция курса не бывает отрицательной" }
        doseAmount?.let { amount ->
            requireNonNegativeDecimal(
                amount = amount,
                field = "Course.doseAmount",
                maxScale = QUANTITY_SCALE,
                maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
            )
            // Нулевая доза — не лечение, а деление на ноль в обеспечении: `dosesIn` на ней бросает.
            require(amount.signum() > 0) { "разовая доза курса строго положительна" }
        }
    }

    /**
     * Доза как величина — только когда известна и единица.
     *
     * Единицу фиксирует первый источник, а дозу человек задаёт сам, и порядок бывает любым:
     * у черновика законно «две штуки чего-то» без единицы и «пачка выбрана» без дозы.
     */
    val dose: Quantity?
        get() = if (doseAmount != null && unitId != null) Quantity(doseAmount, unitId) else null

    /**
     * Название и заметка правятся в любом состоянии, включая действующий курс: это не изменение
     * назначенного лечения (PLAN D5).
     *
     * [revision] при этом не растёт. Редакция отмечает изменение расписания и источников, и
     * приёмы связаны с ней через `Intake.courseRevision`; поднимать её на переименовании значило
     * бы объявлять уже материализованные пункты устаревшими из-за исправленной опечатки.
     */
    fun rename(title: String, note: String?, at: Instant): Course =
        changed(title = title, note = note, updatedAt = at)

    /**
     * Доза задаётся только у черновика.
     *
     * После активации доза, единица, форма и расписание неизменны: изменившееся лечение — это
     * отмена прежнего курса с сохранением истории и создание нового (PLAN D5). Иначе прошлые
     * приёмы остались бы записанными в дозе, которой у курса больше нет.
     */
    fun setDraftDose(amount: BigDecimal, at: Instant): Course {
        requireDraft("доза")
        return changed(doseAmount = amount, revision = revision + 1, updatedAt = at)
    }

    /**
     * Расписание задаётся только у черновика — по той же причине, что и доза.
     *
     * Редакция растёт: расписание меняет состав будущих пунктов, и приёмы связаны с ней через
     * `Intake.courseRevision`. Прошлые пункты при этом не пересоздаются (PLAN D5).
     */
    fun setDraftSchedule(schedule: CourseSchedule, at: Instant): Course {
        requireDraft("расписание")
        return changed(schedule = schedule, revision = revision + 1, updatedAt = at)
    }

    private fun requireDraft(what: String) {
        check(status == CourseStatus.DRAFT) {
            "$what действующего курса неизменна: замена лечения — это новый курс, состояние $status"
        }
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * [id] и [createdAt] в списке отсутствуют: тождество и момент начала курса не меняются.
     */
    private fun changed(
        title: String = this.title,
        note: String? = this.note,
        doseAmount: BigDecimal? = this.doseAmount,
        unitId: Uuid? = this.unitId,
        formId: Uuid? = this.formId,
        schedule: CourseSchedule? = this.schedule,
        status: CourseStatus = this.status,
        revision: Long = this.revision,
        updatedAt: Instant = this.updatedAt
    ): Course = Course(
        id = id,
        title = title,
        note = note,
        doseAmount = doseAmount,
        unitId = unitId,
        formId = formId,
        schedule = schedule,
        status = status,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Тождество — [id]: переименованный курс остаётся тем же курсом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Course && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Course(id=$id, title=$title, status=$status)"
}
