package com.kert0n.medapp.storage.course

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.kert0n.medapp.domain.course.CourseSchedule
import java.time.LocalTime
import kotlin.uuid.Uuid

/**
 * Времена приёма — список, и первичный ключ из пары не даёт завести одно время дважды: два
 * одинаковых времени это один приём, а не два (PLAN F1, D5).
 *
 * Внешнего ключа нет намеренно. `course_id` — тождество **эпизода**, а он переживает план:
 * строки нужны и черновику, у которого записи ещё нет, и закрытой записи, у которой плана уже
 * нет. Ключ на любую из двух таблиц отрезал бы один из этих двух случаев.
 */
@Entity(
    tableName = "course_times",
    primaryKeys = ["course_id", "minutes_of_day"]
)
class CourseTimeStorageEntity(
    @ColumnInfo(name = "course_id") val courseId: Uuid,
    @ColumnInfo(name = "minutes_of_day") val timeOfDay: LocalTime
)

fun CourseSchedule.toTimeStorageEntities(courseId: Uuid): List<CourseTimeStorageEntity> =
    times.map { CourseTimeStorageEntity(courseId = courseId, timeOfDay = it) }
