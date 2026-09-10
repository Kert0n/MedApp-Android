package com.kert0n.medapp.storage.intake

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.network.intake.IntakeAccounting
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {

    @Query("SELECT * FROM intakes WHERE id = :id")
    suspend fun find(id: Uuid): IntakeStorageEntity?

    @Query("SELECT * FROM intakes WHERE course_id = :courseId ORDER BY scheduled_at")
    fun observeOfCourse(courseId: Uuid): Flow<List<IntakeStorageEntity>>

    @Query(
        "SELECT * FROM intakes WHERE status = 'PLANNED' AND scheduled_at < :until " +
            "ORDER BY scheduled_at"
    )
    suspend fun plannedBefore(until: Instant): List<IntakeStorageEntity>

    @Upsert
    suspend fun upsert(intake: IntakeStorageEntity)

    /**
     * Материализация окна идемпотентна: пункт узнают по курсу и исходным дате и времени, и
     * повторный проход не заводит второй такой же (PLAN F4).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlannedIfMissing(intakes: List<IntakeStorageEntity>): List<Long>

    /**
     * Идемпотентность ответа: переход идёт условным `UPDATE` по ожидаемому статусу, а не
     * вставкой и не чтением с последующей записью. Ноль изменённых строк означает, что приём
     * уже отвечен, и повтор ничего не списывает (PLAN D6, F2).
     */
    @Query(
        "UPDATE intakes SET status = :to, answered_at = :at, " +
            "taken_package_id = :packageId, taken_med_kit_id = :medKitId, " +
            "taken_amount = :amount, unit_id = :unitId, accounting = :accounting, " +
            "operation_id = :operationId " +
            "WHERE id = :id AND status IN (:from)"
    )
    suspend fun answerIfStatusIs(
        id: Uuid,
        from: List<IntakeStatus>,
        to: IntakeStatus,
        at: Instant,
        packageId: Uuid?,
        medKitId: Uuid?,
        amount: String?,
        unitId: Uuid,
        accounting: IntakeAccounting,
        operationId: Uuid?
    ): Int

    @Query("DELETE FROM intakes WHERE id = :id")
    suspend fun delete(id: Uuid)
}
