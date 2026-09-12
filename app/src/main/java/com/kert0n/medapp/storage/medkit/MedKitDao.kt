package com.kert0n.medapp.storage.medkit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
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

    @Query("SELECT * FROM med_kits ORDER BY name")
    suspend fun all(): List<MedKitStorageEntity>

    /**
     * Содержимое всех аптечек одним чтением: спрашивать про каждую значило бы сто запросов на
     * список из ста. Считаются живые упаковки — архивированные в аптечке уже не лежат.
     */
    @Query(
        """
        SELECT p.med_kit_id AS med_kit_id,
               COUNT(*) AS packages,
               SUM(CASE WHEN d.expires_on IS NOT NULL AND d.expires_on < :today THEN 1 ELSE 0 END) AS expired
        FROM packages p
        JOIN package_details d ON d.package_id = p.id
        WHERE p.lifecycle = 'ACTIVE'
        GROUP BY p.med_kit_id
        """
    )
    suspend fun contents(today: LocalDate): List<MedKitContentsStorageRow>

    @Query("SELECT * FROM med_kits WHERE id = :id")
    suspend fun find(id: Uuid): MedKitStorageEntity?

    /** Пустую строку аптечки: содержимое к этому моменту либо переехало, либо удалено (PLAN E6). */
    @Query("DELETE FROM med_kits WHERE id = :id")
    suspend fun delete(id: Uuid): Int
}
