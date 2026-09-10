package com.kert0n.medapp.storage.course

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseRecord
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение лечения. Черновик и живой план лежат одной таблицей и различаются тем, где живёт имя,
 * поэтому спрашивают их порознь: у экрана черновика и экрана курса разные вопросы (PLAN D5, F1).
 */
interface CourseStorageRepository {

    fun observeDrafts(): Flow<List<CourseDraft>>

    fun observePlan(id: Uuid): Flow<Course?>

    suspend fun findDraft(id: Uuid): CourseDraft?

    suspend fun findPlan(id: Uuid): Course?

    /** Черновик целиком: назначение, времена и источники по порядку. */
    suspend fun saveDraft(draft: CourseDraft)

    /** Аналитика читает записи: идущее и законченное лечение для неё одной формы (PLAN H6). */
    fun observeRecords(): Flow<List<CourseRecord>>

    fun observeRecord(id: Uuid): Flow<CourseRecord?>

    suspend fun findRecord(id: Uuid): CourseRecord?

    suspend fun saveRecord(record: CourseRecord)

    /** Какому активному курсу отдана пачка; `null` — она свободна (PLAN F1, F2). */
    suspend fun courseHolding(packageId: Uuid): Uuid?
}
