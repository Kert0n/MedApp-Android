package com.kert0n.medapp.storage.course

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.course.CourseSchedule
import com.kert0n.medapp.domain.course.Prescription
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedForm
import com.kert0n.medapp.storage.value.storedUnit

/**
 * Запись эпизода вместе с временами своего назначения: они лежат в `course_times` по тождеству
 * эпизода и остаются на месте, когда план уже удалён (PLAN D5).
 */
class CourseRecordStorageRow(
    @Embedded val record: CourseRecordStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "course_id")
    val times: List<CourseTimeStorageEntity> = emptyList()
) {
    fun toDomain(vocabulary: Vocabulary): CourseRecord = CourseRecord(
        id = record.id,
        title = record.title,
        note = record.note,
        prescription = Prescription(
            dose = storedDose(record.doseAmount, vocabulary.storedUnit(record.unitId)),
            form = vocabulary.storedForm(record.formId),
            totalDoses = Doses(record.totalDoses),
            schedule = CourseSchedule(
                start = record.start,
                daysOfWeek = record.daysOfWeek,
                times = times.map { it.timeOfDay }.sorted(),
                zone = record.zone
            )
        ),
        startedAt = record.startedAt,
        outcome = record.outcome,
        closedAt = record.closedAt
    )
}
