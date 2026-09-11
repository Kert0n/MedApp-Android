package com.kert0n.medapp.storage.course

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Живой план лечения. Расписание встроено колонками: отдельной жизни у него нет, а отдельная
 * таблица добавила бы только join. Состояний у строки нет — она живёт, пока лечение идёт, и
 * удаляется при его конце (PLAN D5, F1).
 *
 * Черновик — та же таблица без назначения: `dose_amount` и расписание у него могут отсутствовать.
 * `title` и `note` заполнены **только** у черновика: при активации имя лечения переезжает в
 * запись эпизода, и второго живого места для него не остаётся. Поэтому непустой `title` здесь
 * и означает «лечение ещё не началось».
 */
@Entity(tableName = "courses")
class CourseStorageEntity(
    @PrimaryKey val id: Uuid,
    val title: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "dose_amount") val doseAmount: String? = null,
    @ColumnInfo(name = "unit_id") val unitId: Uuid? = null,
    @ColumnInfo(name = "form_id") val formId: Uuid? = null,
    val start: LocalDate? = null,
    @ColumnInfo(name = "end_inclusive") val endInclusive: LocalDate? = null,
    @ColumnInfo(name = "days_mask") val daysOfWeek: Set<DayOfWeek>? = null,
    val zone: ZoneId? = null,
    val revision: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)

fun CourseDraft.toStorageEntity(): CourseStorageEntity = CourseStorageEntity(
    id = id,
    title = title,
    note = note,
    doseAmount = doseAmount?.toPlainString(),
    unitId = medicine.unit?.id,
    formId = medicine.form?.id,
    start = schedule?.start,
    endInclusive = schedule?.endInclusive,
    daysOfWeek = schedule?.daysOfWeek,
    zone = schedule?.zone,
    revision = revision.number,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** У начатого лечения имя живёт в записи эпизода, поэтому `title` и `note` здесь пусты. */
fun Course.toStorageEntity(): CourseStorageEntity = CourseStorageEntity(
    id = id,
    doseAmount = dose.quantity.toStorageAmount(),
    unitId = medicine.unit?.id,
    formId = medicine.form?.id,
    start = schedule.start,
    endInclusive = schedule.endInclusive,
    daysOfWeek = schedule.daysOfWeek,
    zone = schedule.zone,
    revision = revision.number,
    createdAt = createdAt,
    updatedAt = updatedAt
)
