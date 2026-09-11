package com.kert0n.medapp.storage.course

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Запись эпизода: заводится при активации и живёт вечно. Тождество то же, что у плана, поэтому
 * ссылка приёма никогда не повисает, а аналитика читает идущее и законченное лечение одной
 * формой (PLAN D5, H6).
 *
 * Назначение здесь — снимок: оно переживает план, иначе история приёмов потеряла бы, что было
 * назначено. Времена приёма лежат в `course_times` по тому же тождеству эпизода.
 */
@Entity(tableName = "course_records")
class CourseRecordStorageEntity(
    @PrimaryKey val id: Uuid,
    val title: String,
    val note: String? = null,
    @ColumnInfo(name = "dose_amount") val doseAmount: String,
    @ColumnInfo(name = "unit_id") val unitId: Uuid,
    val start: LocalDate,
    @ColumnInfo(name = "end_inclusive") val endInclusive: LocalDate,
    @ColumnInfo(name = "days_mask") val daysOfWeek: Set<DayOfWeek>,
    val zone: ZoneId,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    val outcome: CourseRecord.Outcome? = null,
    @ColumnInfo(name = "closed_at") val closedAt: Instant? = null
)

fun CourseRecord.toStorageEntity(): CourseRecordStorageEntity = CourseRecordStorageEntity(
    id = id,
    title = title,
    note = note,
    doseAmount = prescription.dose.quantity.toStorageAmount(),
    unitId = prescription.dose.unit.id,
    start = prescription.schedule.start,
    endInclusive = prescription.schedule.endInclusive,
    daysOfWeek = prescription.schedule.daysOfWeek,
    zone = prescription.schedule.zone,
    startedAt = startedAt,
    outcome = outcome,
    closedAt = closedAt
)
