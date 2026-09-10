package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.network.intake.IntakeSyncState
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IntakeRoomRepository @Inject constructor(
    private val intakes: IntakeDao
) : IntakeStorageRepository {

    override fun observeOfCourse(courseId: Uuid): Flow<List<Intake>> =
        intakes.observeOfCourse(courseId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun find(id: Uuid): Intake? = intakes.find(id)?.toDomain()

    override suspend fun syncStateOf(id: Uuid): IntakeSyncState? = intakes.find(id)?.syncState()

    override suspend fun save(intake: Intake, sync: IntakeSyncState) =
        intakes.upsert(intake.toStorageEntity(sync))

    override suspend fun materialise(planned: List<CourseIntake>): Int =
        intakes.insertPlannedIfMissing(planned.map { it.toStorageEntity() })
            .count { it != -1L }

    override suspend fun plannedBefore(until: Instant): List<CourseIntake> =
        intakes.plannedBefore(until).map { it.toDomain() as CourseIntake }
}
