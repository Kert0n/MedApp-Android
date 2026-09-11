package com.kert0n.medapp.storage.course

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseMedicine
import com.kert0n.medapp.domain.course.CourseSchedule
import com.kert0n.medapp.domain.course.Prescription
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedForm
import com.kert0n.medapp.storage.value.storedUnit

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
    @Relation(entity = CourseSourceStorageEntity::class, parentColumn = "id", entityColumn = "course_id")
    val sources: List<CourseSourceStorageRow> = emptyList()
) {
    /** Лечение ещё не начато: имя обязательно, назначение — нет. */
    val isDraft: Boolean get() = course.title != null

    fun toDraft(vocabulary: Vocabulary): CourseDraft = CourseDraft(
        id = course.id,
        title = requireNotNull(course.title) { "у черновика есть имя: без него это уже эпизод" },
        note = course.note,
        dose = course.doseAmount?.let { amount ->
            storedDose(amount, vocabulary.storedUnit(requireNotNull(course.unitId) {
                "доза черновика без единицы не восстанавливается"
            }))
        },
        form = course.formId?.let(vocabulary::storedForm),
        schedule = schedule(),
        totalDoses = course.totalDoses?.let(::Doses),
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
            form = vocabulary.storedForm(
                requireNotNull(course.formId) { "у начатого лечения записана форма" }
            ),
            schedule = requireNotNull(schedule()) { "у начатого лечения расписание назначено" },
            totalDoses = Doses(requireNotNull(course.totalDoses) { "у начатого лечения названо число доз" })
        ),
        medicine = medicine(vocabulary),
        takenOffPlan = Doses(course.takenOffPlan),
        revision = Revision(course.revision),
        createdAt = course.createdAt,
        updatedAt = course.updatedAt
    )

    private fun schedule(): CourseSchedule? {
        val start = course.start ?: return null
        val days = course.daysOfWeek ?: return null
        val zone = course.zone ?: return null
        if (times.isEmpty()) return null
        return CourseSchedule(
            start = start,
            daysOfWeek = days,
            times = times.map { it.timeOfDay }.sorted(),
            zone = zone
        )
    }

    private fun medicine(vocabulary: Vocabulary): CourseMedicine =
        CourseMedicine(sources.sortedBy { it.source.position }.map { it.toDomain(vocabulary) })
}
