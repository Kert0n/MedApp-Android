package com.kert0n.medapp.storage.course

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun findPlan(id: Uuid): CourseStorageRow?

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    fun observePlan(id: Uuid): Flow<CourseStorageRow?>

    /** Черновики — те, у кого имя ещё живёт здесь, то есть лечение не начато (PLAN D5). */
    @Transaction
    @Query("SELECT * FROM courses WHERE title IS NOT NULL ORDER BY updated_at DESC")
    fun observeDrafts(): Flow<List<CourseStorageRow>>

    @Transaction
    @Query("SELECT * FROM courses WHERE title IS NULL ORDER BY created_at")
    fun observePlans(): Flow<List<CourseStorageRow>>

    @Transaction
    @Query("SELECT * FROM course_records WHERE id = :id")
    suspend fun findRecord(id: Uuid): CourseRecordStorageRow?

    @Transaction
    @Query("SELECT * FROM course_records WHERE id = :id")
    fun observeRecord(id: Uuid): Flow<CourseRecordStorageRow?>

    /** Аналитика читает записи: идущее и законченное лечение для неё одной формы (PLAN H6). */
    @Transaction
    @Query("SELECT * FROM course_records ORDER BY started_at DESC")
    fun observeRecords(): Flow<List<CourseRecordStorageRow>>

    /**
     * Черновик целиком: план, его времена и его источники. Времена и источники переписываются
     * заменой — редакция черновика описывает набор, а не разницу с прошлым набором.
     */
    @Transaction
    suspend fun saveCourse(
        course: CourseStorageEntity,
        times: List<CourseTimeStorageEntity>,
        sources: List<CourseSourceStorageEntity>
    ) {
        upsertCourse(course)
        deleteSourcesOf(course.id)
        deleteTimesOf(course.id)
        insertTimes(times)
        insertSources(sources)
    }

    @Upsert
    suspend fun upsertCourse(course: CourseStorageEntity)

    @Upsert
    suspend fun upsertRecord(record: CourseRecordStorageEntity)

    @Insert
    suspend fun insertTimes(times: List<CourseTimeStorageEntity>)

    @Insert
    suspend fun insertSources(sources: List<CourseSourceStorageEntity>)

    @Query("DELETE FROM course_times WHERE course_id = :courseId")
    suspend fun deleteTimesOf(courseId: Uuid)

    @Query("DELETE FROM course_sources WHERE course_id = :courseId")
    suspend fun deleteSourcesOf(courseId: Uuid)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deletePlan(id: Uuid)
}
