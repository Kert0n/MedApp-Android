package com.kert0n.medapp.storage.medkit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@Dao
interface MedKitDao {

    @Upsert
    suspend fun upsert(medKit: MedKitStorageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(medKit: MedKitStorageEntity)

    /**
     * Снимок трогает только число участников: название и место хранения серверу неизвестны,
     * и точечный `UPDATE` не даёт им пропасть (PLAN F1, E4).
     */
    @Query(
        "UPDATE med_kits SET participant_count = :participantCount, synced_at = :syncedAt " +
            "WHERE id = :id"
    )
    suspend fun applyServerParticipants(id: Uuid, participantCount: Long, syncedAt: Instant)

    @Query("SELECT * FROM med_kits WHERE id = :id")
    fun observe(id: Uuid): Flow<MedKitStorageEntity?>

    @Query("SELECT * FROM med_kits ORDER BY name")
    fun observeAll(): Flow<List<MedKitStorageEntity>>

    @Query("SELECT * FROM med_kits WHERE id = :id")
    suspend fun find(id: Uuid): MedKitStorageEntity?

    @Query("DELETE FROM med_kits WHERE id = :id")
    suspend fun delete(id: Uuid)
}
