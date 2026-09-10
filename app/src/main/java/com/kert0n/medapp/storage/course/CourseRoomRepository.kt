package com.kert0n.medapp.storage.course

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.CourseRecord
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CourseRoomRepository @Inject constructor(
    private val courses: CourseDao
) : CourseStorageRepository {

    override fun observeDrafts(): Flow<List<CourseDraft>> =
        courses.observeDrafts().map { rows -> rows.map { it.toDraft() } }

    override fun observePlan(id: Uuid): Flow<Course?> =
        courses.observePlan(id).map { row -> row?.takeUnless { it.isDraft }?.toPlan() }

    override suspend fun findDraft(id: Uuid): CourseDraft? =
        courses.findPlan(id)?.takeIf { it.isDraft }?.toDraft()

    override suspend fun findPlan(id: Uuid): Course? =
        courses.findPlan(id)?.takeUnless { it.isDraft }?.toPlan()

    override suspend fun saveDraft(draft: CourseDraft) = courses.saveCourse(
        course = draft.toStorageEntity(),
        times = draft.schedule?.toTimeStorageEntities(draft.id).orEmpty(),
        sources = draft.medicine.toSourceStorageEntities(draft.id)
    )

    override fun observeRecords(): Flow<List<CourseRecord>> =
        courses.observeRecords().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecord(id: Uuid): Flow<CourseRecord?> =
        courses.observeRecord(id).map { it?.toDomain() }

    override suspend fun findRecord(id: Uuid): CourseRecord? =
        courses.findRecord(id)?.toDomain()

    override suspend fun saveRecord(record: CourseRecord) =
        courses.upsertRecord(record.toStorageEntity())

    override suspend fun courseHolding(packageId: Uuid): Uuid? = courses.courseHolding(packageId)
}
