package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс — лечение, которое человек себе назначил: сколько принимать, по какому календарю и из
 * каких пачек.
 *
 * **Это два состояния с разным поведением, а не одно с четырьмя необязательными полями.**
 * У черновика может не быть ни расписания, ни дозы, ни источников: «записал у врача → купил →
 * внёс» начинается раньше, чем известны даты и пачки (PLAN D5). У назначенного курса они есть
 * всегда — и неизменны. Пока это был один класс, разницу держали `require(status != ACTIVE || …)`
 * и четыре `requireNotNull(course.dose)` в вычислителях; курс без дозы всё равно можно было
 * передать в обеспечение, и падало оно уже там.
 *
 * Теперь [CourseDraft] и [PlannedCourse] — разные типы, а вычислители принимают второй: курса без
 * дозы в них просто не приходит.
 *
 * **Сущность:** переименованный курс — тот же курс, и приёмы, уже порождённые им, остаются его
 * приёмами. Тождество — [id], равенство по нему.
 *
 * Курс на сервер не уезжает вовсе (PLAN C0): расписаний, приёмов и курсов там нет и не будет,
 * поэтому обвязки синхронизации у него нет по построению, а не по решению. Уезжает только
 * следствие выделения — серверная бронь на упаковку (PLAN D5, E2).
 */
sealed interface Course {

    val id: Uuid
    val title: String

    /** «Что купить», запись от врача. */
    val note: String?

    /** Стек источников: порядок есть приоритет расходования (PLAN D5). */
    val stack: SourceStack

    val status: CourseStatus

    /** Редакция черновика и источников; у назначенного курса доза и расписание неизменны. */
    val revision: Long

    val createdAt: Instant
    val updatedAt: Instant

    /** Разовая доза. `null` только у черновика, которому её ещё не задали. */
    val dose: Quantity?

    /** Расписание. `null` только у черновика. */
    val schedule: CourseSchedule?

    val sources: List<CourseSource> get() = stack.items

    val formId: Uuid? get() = stack.formId

    val unitId: Uuid? get() = stack.unitId

    val allocatedDosesTotal: Doses get() = stack.allocatedTotal

    /**
     * Выделение источника **в единицах пачки** — та самая величина, которую видит серверная
     * бронь: целевой объём брони равен `allocatedDoses × dose` (PLAN D5).
     *
     * `null`, когда пачка не источник этого курса или когда доза ещё не задана: выдумывать
     * количество из неизвестной дозы нельзя.
     */
    fun allocatedOf(packageId: Uuid): Quantity? {
        val allocated = stack.allocatedTo(packageId) ?: return null
        return dose?.times(allocated)
    }
}
