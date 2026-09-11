package com.kert0n.medapp.storage.course

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.kert0n.medapp.domain.course.Revision
import java.time.Instant
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

    /**
     * Пересчитанные выделения живого плана. Запись условна по редакции: план, закрытый или уже
     * пересчитанный между чтением и записью, не возвращается и не переписывается результатом,
     * посчитанным из прошлого состава — ноль изменённых строк значит, что писать некуда
     * (PLAN D5, F5).
     *
     * Меняются только редакция, время правки и источники: доза и расписание действующего курса
     * неизменны, и пересчёт обеспечения их не касается.
     */
    @Transaction
    suspend fun updateAllocations(
        course: CourseStorageEntity,
        sources: List<CourseSourceStorageEntity>,
        expected: Revision
    ): Boolean {
        if (reviseIfRevisionIs(course.id, expected.number, course.revision, course.updatedAt) == 0) {
            // Ноль строк законен ровно в одном случае: плана больше нет, писать некуда. Живой
            // план другой редакции — пересчёт из устаревшего состава, и молча пропустить его
            // нельзя: транзакция вокруг уже записала расход, обеспечение которого он и считал.
            check(findPlan(course.id) == null) {
                "выделения посчитаны из редакции ${expected.number}, а план уже другой"
            }
            return false
        }
        deleteSourcesOf(course.id)
        insertSources(sources)
        return true
    }

    @Query(
        "UPDATE courses SET revision = :revision, updated_at = :updatedAt " +
            "WHERE id = :id AND revision = :expected"
    )
    suspend fun reviseIfRevisionIs(id: Uuid, expected: Long, revision: Long, updatedAt: Instant): Int

    /**
     * Число доз правится у плана и в снимке записи одной транзакцией: назначение лежит в двух
     * строках и разойтись им нельзя (PLAN F5). Запись условна по редакции, как и выделения; ноль
     * строк значит «плана уже нет», и снимок записи тогда тоже не трогается.
     */
    @Transaction
    suspend fun updateTotalDoses(
        id: Uuid,
        totalDoses: Int,
        expected: Revision,
        revision: Revision,
        updatedAt: Instant
    ): Boolean {
        if (setTotalDosesIfRevisionIs(id, expected.number, totalDoses, revision.number, updatedAt) == 0) {
            return false
        }
        setRecordTotalDoses(id, totalDoses)
        return true
    }

    @Query(
        "UPDATE courses SET total_doses = :totalDoses, revision = :revision, updated_at = :updatedAt " +
            "WHERE id = :id AND revision = :expected"
    )
    suspend fun setTotalDosesIfRevisionIs(
        id: Uuid,
        expected: Long,
        totalDoses: Int,
        revision: Long,
        updatedAt: Instant
    ): Int

    @Query("UPDATE course_records SET total_doses = :totalDoses WHERE id = :id")
    suspend fun setRecordTotalDoses(id: Uuid, totalDoses: Int)

    @Upsert
    suspend fun upsertCourse(course: CourseStorageEntity)

    @Upsert
    suspend fun upsertRecord(record: CourseRecordStorageEntity)

    /** Правится только то, что человек и назвал: назначение, начало и исход остаются на месте. */
    @Query("UPDATE course_records SET title = :title, note = :note WHERE id = :id")
    suspend fun rename(id: Uuid, title: String, note: String?): Int

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

    /**
     * Назначение пачки активному курсу. Вставка без стратегии конфликта намеренно: второе
     * назначение должно быть отвергнуто базой, а не пережить проверку «а нет ли уже» (PLAN F2).
     */
    @Insert
    suspend fun assignPackage(assignment: ActivePackageAssignmentStorageEntity)

    @Query("SELECT course_id FROM active_package_assignments WHERE package_id = :packageId")
    suspend fun courseHolding(packageId: Uuid): Uuid?

    @Query("SELECT * FROM active_package_assignments WHERE course_id = :courseId")
    suspend fun assignmentsOf(courseId: Uuid): List<ActivePackageAssignmentStorageEntity>

    @Query("DELETE FROM active_package_assignments WHERE package_id = :packageId")
    suspend fun releasePackage(packageId: Uuid)

    @Query("DELETE FROM active_package_assignments WHERE course_id = :courseId")
    suspend fun releaseAssignmentsOf(courseId: Uuid)
}
