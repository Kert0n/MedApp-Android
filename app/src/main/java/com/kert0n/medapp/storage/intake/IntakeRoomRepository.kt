package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.UnplannedIntake
import androidx.room.withTransaction
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity as toPackageStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IntakeRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val intakes: IntakeDao,
    private val packages: PackageDao,
    private val courses: CourseDao,
    private val movements: StockMovementDao,
    private val queue: SyncOperationDao
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

    override suspend fun record(outcome: IntakeOutcome): Boolean = database.withTransaction {
        val intake = outcome.intake
        val applied = if (intake is UnplannedIntake) {
            intakes.insertIfMissing(intake.toStorageEntity(outcome.sync)) != -1L
        } else {
            val taken = intake.taken
            intakes.answerIfStatusIs(
                id = intake.id,
                from = outcome.expected.toList(),
                to = intake.status,
                at = outcome.answeredAt,
                packageId = taken?.packageId,
                medKitId = taken?.medKitId,
                amount = taken?.amount?.quantity?.toStorageAmount(),
                unitId = intake.unitId,
                accounting = outcome.sync.accounting,
                operationId = outcome.sync.operationId
            ) > 0
        }
        if (!applied) return@withTransaction false

        outcome.spent?.let {
            // Расход не трогает обвязку доставки: версия и картина броней остаются прежними (E3).
            val sync = packages.find(it.id)?.pack?.syncState() ?: PackageSyncState(it.id)
            packages.save(it.toPackageStorageEntity(sync), it.toDetailsStorageEntity())
        }
        outcome.movement?.let { movements.insert(it.toMovementStorageEntity()) }
        outcome.course?.let {
            courses.updateAllocations(
                it.toCourseStorageEntity(),
                it.medicine.toSourceStorageEntities(it.id)
            )
        }
        outcome.command?.let {
            queue.enqueue(
                id = it.id,
                command = it.command,
                createdAt = outcome.answeredAt,
                groupId = it.groupId,
                dependsOn = it.dependsOn
            )
        }
        true
    }

}
