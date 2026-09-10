package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс — лечение, которое человек себе назначил: сколько принимать, по какому календарю и из
 * каких пачек. Сущность: переименованный курс — тот же курс, равенство по [id]. Типов два, потому
 * что ведут они себя по-разному: у [CourseDraft] дозы, расписания и пачек может ещё не быть, у
 * [PlannedCourse] они есть, а доза и расписание неизменны (PLAN D5). На сервер курс не уезжает
 * (C0) — уезжает только бронь, следствие выделения.
 */
sealed interface Course {

    val id: Uuid
    val title: String

    /** «Что купить», запись от врача. */
    val note: String?

    /** Препарат курса: пачки, порядок их расходования и выделение. */
    val medicine: CourseMedicine

    val status: CourseStatus

    /** Редакция черновика и источников; у назначенного курса доза и расписание неизменны. */
    val revision: Revision

    val createdAt: Instant
    val updatedAt: Instant

    /** Разовая доза. `null` только у черновика, которому её ещё не задали. */
    val dose: Quantity?

    /** Расписание. `null` только у черновика. */
    val schedule: CourseSchedule?

    val sources: List<CourseSource> get() = medicine.sources

    val formId: Uuid? get() = medicine.formId

    val unitId: Uuid? get() = medicine.unitId

    val allocatedDosesTotal: Doses get() = medicine.allocatedTotal

    /**
     * Выделение источника **в единицах пачки** — та самая величина, которую видит серверная
     * бронь: целевой объём брони равен `allocatedDoses × dose` (PLAN D5).
     *
     * `null`, когда пачка не источник этого курса или когда доза ещё не задана: выдумывать
     * количество из неизвестной дозы нельзя.
     */
    fun allocatedOf(packageId: Uuid): Quantity? {
        val allocated = medicine.allocatedTo(packageId) ?: return null
        return dose?.times(allocated)
    }

    companion object {
        const val TITLE_MAX_LENGTH = 200

        /** Длиннее названия: сюда переписывают запись от врача и «что купить». */
        const val NOTE_MAX_LENGTH = 500
    }
}
