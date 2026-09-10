package com.kert0n.medapp.storage.stock

import com.kert0n.medapp.domain.stock.StockMovement
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StockMovementRoomRepository @Inject constructor(
    private val movements: StockMovementDao
) : StockMovementStorageRepository {

    override suspend fun record(movement: StockMovement) =
        movements.insert(movement.toStorageEntity())

    override fun observeOfPackage(packageId: Uuid): Flow<List<StockMovement>> =
        movements.observeOfPackage(packageId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun ofPackage(packageId: Uuid): List<StockMovement> =
        movements.ofPackage(packageId).map { it.toDomain() }

    override suspend fun observedBetween(from: Instant, until: Instant): List<StockMovement> =
        movements.observedBetween(from, until).map { it.toDomain() }
}
