package com.kert0n.medapp.storage.course

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseMedicine
import com.kert0n.medapp.domain.course.CourseSchedule
import com.kert0n.medapp.domain.course.CourseSource
import com.kert0n.medapp.domain.course.Prescription
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedForm
import com.kert0n.medapp.storage.value.storedUnit
import java.math.BigDecimal

/**
 * Курс, собранный из своих строк: сам план, его времена и его источники по порядку.
 *
 * Одна и та же строка отдаёт либо черновик, либо живой план — их различает не колонка-состояние,
 * а наличие записи эпизода: у начатого лечения имя живёт в записи, у черновика — здесь
 * (PLAN D5, F1).
 */
class CourseStorageRow(
    @Embedded val course: CourseStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "course_id")
    val times: List<CourseTimeStorageEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "course_id")
    val sources: List<CourseSourceStorageEntity> = emptyList()
) {
    /** Лечение ещё не начато: имя обязательно, назначение — нет. */
    val isDraft: Boolean get() = course.title != null

    fun toDraft(vocabulary: Vocabulary): CourseDraft = CourseDraft(
        id = course.id,
        title = requireNotNull(course.title) { "у черновика есть имя: без него это уже эпизод" },
        note = course.note,
        doseAmount = course.doseAmount?.let(::BigDecimal),
        schedule = schedule(),
        medicine = medicine(vocabulary),
        revision = Revision(course.revision),
        createdAt = course.createdAt,
        updatedAt = course.updatedAt
    )

    fun toPlan(vocabulary: Vocabulary): Course = Course(
        id = course.id,
        prescription = Prescription(
            dose = storedDose(
                requireNotNull(course.doseAmount) { "у начатого лечения доза назначена" },
                vocabulary.storedUnit(
                    requireNotNull(course.unitId) { "у начатого лечения записана единица дозы" }
                )
            ),
            schedule = requireNotNull(schedule()) { "у начатого лечения расписание назначено" }
        ),
        medicine = medicine(vocabulary),
        revision = Revision(course.revision),
        createdAt = course.createdAt,
        updatedAt = course.updatedAt
    )

    private fun schedule(): CourseSchedule? {
        val start = course.start ?: return null
        val endInclusive = course.endInclusive ?: return null
        val days = course.daysOfWeek ?: return null
        val zone = course.zone ?: return null
        if (times.isEmpty()) return null
        return CourseSchedule(
            start = start,
            endInclusive = endInclusive,
            daysOfWeek = days,
            times = times.map { it.timeOfDay }.sorted(),
            zone = zone
        )
    }

    private fun medicine(vocabulary: Vocabulary): CourseMedicine = CourseMedicine(
        sources = sources.sortedBy { it.position }.map {
            CourseSource(packageId = it.packageId, allocatedDoses = Doses(it.allocatedDoses))
        },
        form = course.formId?.let(vocabulary::storedForm),
        unit = course.unitId?.let(vocabulary::storedUnit)
    )
}
