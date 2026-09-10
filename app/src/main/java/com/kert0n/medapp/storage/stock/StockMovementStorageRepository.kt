package com.kert0n.medapp.storage.stock

import com.kert0n.medapp.domain.stock.StockMovement
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение движений остатка. Записи только добавляются: переписать прошлое движение значит
 * переписать историю (PLAN D7).
 */
interface StockMovementStorageRepository {

    suspend fun record(movement: StockMovement)

    fun observeOfPackage(packageId: Uuid): Flow<List<StockMovement>>

    suspend fun ofPackage(packageId: Uuid): List<StockMovement>

    suspend fun observedBetween(from: Instant, until: Instant): List<StockMovement>
}
