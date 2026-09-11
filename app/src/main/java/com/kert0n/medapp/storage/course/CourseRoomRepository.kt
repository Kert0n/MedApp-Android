package com.kert0n.medapp.storage.course

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import androidx.room.withTransaction
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeAnswer
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.intake.toStorageEntity as toIntakeStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CourseRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val courses: CourseDao,
    private val intakes: IntakeDao,
    private val vocabulary: VocabularyDao
) : CourseStorageRepository {

    override fun observeDrafts(): Flow<List<CourseDraft>> =
        courses.observeDrafts().map { rows ->
            val words = vocabulary.snapshot()
            rows.map { it.toDraft(words) }
        }

    override fun observePlan(id: Uuid): Flow<Course?> =
        courses.observePlan(id).map { row ->
            row?.takeUnless { it.isDraft }?.toPlan(vocabulary.snapshot())
        }

    override suspend fun findDraft(id: Uuid): CourseDraft? =
        courses.findPlan(id)?.takeIf { it.isDraft }?.toDraft(vocabulary.snapshot())

    override suspend fun findPlan(id: Uuid): Course? =
        courses.findPlan(id)?.takeUnless { it.isDraft }?.toPlan(vocabulary.snapshot())

    override suspend fun saveDraft(draft: CourseDraft): Boolean = database.withTransaction {
        val existing = courses.findPlan(draft.id)
        if (existing != null && !existing.isDraft) return@withTransaction false
        // Запись эпизода живёт вечно, а план после конца лечения удаляется: «плана нет» само по
        // себе не значит «черновик ещё можно сохранить».
        if (existing == null && courses.findRecord(draft.id) != null) return@withTransaction false
        courses.saveCourse(
            course = draft.toStorageEntity(),
            times = draft.schedule?.toTimeStorageEntities(draft.id).orEmpty(),
            sources = draft.medicine.toSourceStorageEntities(draft.id)
        )
        true
    }

    override fun observeRecords(): Flow<List<CourseRecord>> =
        courses.observeRecords().map { rows ->
            val words = vocabulary.snapshot()
            rows.map { it.toDomain(words) }
        }

    override fun observeRecord(id: Uuid): Flow<CourseRecord?> =
        courses.observeRecord(id).map { it?.toDomain(vocabulary.snapshot()) }

    override suspend fun findRecord(id: Uuid): CourseRecord? =
        courses.findRecord(id)?.toDomain(vocabulary.snapshot())

    override suspend fun rename(id: Uuid, title: String, note: String?): Boolean =
        courses.rename(id, title, note) > 0

    override suspend fun courseHolding(packageId: Uuid): Uuid? = courses.courseHolding(packageId)

    override suspend fun reallocate(reallocation: CourseReallocation): Boolean =
        courses.updateAllocations(
            reallocation.course.toStorageEntity(),
            reallocation.course.medicine.toSourceStorageEntities(reallocation.course.id),
            reallocation.expected
        )

    override suspend fun setTotalDoses(course: Course, expected: Revision): Boolean =
        courses.updateTotalDoses(
            id = course.id,
            totalDoses = course.totalDoses.count,
            expected = expected,
            revision = course.revision,
            updatedAt = course.updatedAt
        )

    override suspend fun activate(
        activation: CourseDraft.Activation,
        planned: List<CourseIntake>
    ) = database.withTransaction {
        val plan = activation.course
        courses.upsertRecord(activation.record.toStorageEntity())
        courses.saveCourse(
            course = plan.toStorageEntity(),
            times = plan.schedule.toTimeStorageEntities(plan.id),
            sources = plan.medicine.toSourceStorageEntities(plan.id)
        )
        for (source in plan.sources) {
            courses.assignPackage(ActivePackageAssignmentStorageEntity(source.packageId, plan.id))
        }
        intakes.insertPlannedIfMissing(planned.map { it.toIntakeStorageEntity() })
        Unit
    }

    override suspend fun close(
        record: CourseRecord,
        cancelled: List<CourseIntake>
    ) = database.withTransaction {
        check(!record.isOpen) { "закрывается законченное лечение, а не идущее" }
        courses.upsertRecord(record.toStorageEntity())
        for (intake in cancelled) {
            val cancellation = requireNotNull(intake.answer as? IntakeAnswer.Cancelled) {
                "конец лечения отменяет пункт, а не отвечает на него"
            }
            intakes.cancelIfPlanned(intake.id, cancellation.at)
        }
        courses.releaseAssignmentsOf(record.id)
        courses.deleteSourcesOf(record.id)
        courses.deletePlan(record.id)
    }
}
