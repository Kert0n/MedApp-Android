package com.kert0n.medapp.storage.stock

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {

    /**
     * Движение только заводится: это учётная запись, а не изменяемое состояние. Переписать
     * прошлое движение значит переписать историю (PLAN D7).
     */
    @Insert
    suspend fun insert(movement: StockMovementStorageEntity)

    @Query("SELECT * FROM stock_adjustments WHERE package_id = :packageId ORDER BY observed_at")
    fun observeOfPackage(packageId: Uuid): Flow<List<StockMovementStorageEntity>>

    @Query(
        "SELECT * FROM stock_adjustments WHERE observed_at >= :from AND observed_at < :until " +
            "ORDER BY observed_at"
    )
    suspend fun observedBetween(from: Instant, until: Instant): List<StockMovementStorageEntity>

    @Query("SELECT * FROM stock_adjustments WHERE package_id = :packageId ORDER BY observed_at")
    suspend fun ofPackage(packageId: Uuid): List<StockMovementStorageEntity>
}
