package com.kert0n.medapp.storage.stock

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StockMovementRoomRepository @Inject constructor(
    private val movements: StockMovementDao,
    private val vocabulary: VocabularyDao
) : StockMovementStorageRepository {

    override suspend fun record(movement: StockMovement) =
        movements.insert(movement.toStorageEntity())

    override fun observeOfPackage(packageId: Uuid): Flow<List<StockMovement>> =
        movements.observeOfPackage(packageId).map { rows -> rows.toDomain() }

    override suspend fun ofPackage(packageId: Uuid): List<StockMovement> =
        movements.ofPackage(packageId).toDomain()

    override suspend fun observedBetween(from: Instant, until: Instant): List<StockMovement> =
        movements.observedBetween(from, until).toDomain()

    private suspend fun List<StockMovementStorageEntity>.toDomain(): List<StockMovement> {
        val words = vocabulary.snapshot()
        return map { it.toDomain(words) }
    }
}
