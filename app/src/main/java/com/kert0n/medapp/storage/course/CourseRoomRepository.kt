package com.kert0n.medapp.storage.course

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseDraft
import androidx.room.withTransaction
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.intake.toStorageEntity as toIntakeStorageEntity
import com.kert0n.medapp.storage.server.QueuedCommand
import com.kert0n.medapp.storage.server.SyncOperationDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CourseRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val courses: CourseDao,
    private val intakes: IntakeDao,
    private val queue: SyncOperationDao
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

    override suspend fun activate(
        activation: CourseDraft.Activation,
        planned: List<CourseIntake>,
        commands: List<QueuedCommand>,
        at: Instant
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
        enqueue(commands, at)
    }

    override suspend fun close(
        record: CourseRecord,
        cancelled: List<CourseIntake>,
        commands: List<QueuedCommand>,
        at: Instant
    ) = database.withTransaction {
        check(!record.isOpen) { "закрывается законченное лечение, а не идущее" }
        courses.upsertRecord(record.toStorageEntity())
        for (intake in cancelled) intakes.upsert(intake.toIntakeStorageEntity())
        courses.releaseAssignmentsOf(record.id)
        courses.deleteSourcesOf(record.id)
        courses.deletePlan(record.id)
        enqueue(commands, at)
    }

    private suspend fun enqueue(commands: List<QueuedCommand>, at: Instant) {
        for (command in commands) {
            queue.enqueue(
                id = command.id,
                command = command.command,
                createdAt = at,
                groupId = command.groupId,
                dependsOn = command.dependsOn
            )
        }
    }
}
