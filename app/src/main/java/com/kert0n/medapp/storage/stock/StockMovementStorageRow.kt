package com.kert0n.medapp.storage.stock

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.pack.PackageRecordStorageEntity
import com.kert0n.medapp.storage.pack.PackageRefStorageRow
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.storedUnit
import java.math.BigDecimal

/**
 * Движение вместе с пачкой, о которой оно написано. Домен держит её ссылкой; Room читает связь
 * той же транзакцией и по одному запросу на всю выборку — история из ста строк грузит пачки одним
 * запросом, а не ста.
 */
class StockMovementStorageRow(
    @Embedded val movement: StockMovementStorageEntity,
    @Relation(entity = PackageRecordStorageEntity::class, parentColumn = "package_id", entityColumn = "id")
    val pack: PackageRefStorageRow? = null
) {
    fun toDomain(vocabulary: Vocabulary): StockMovement {
        val unit = vocabulary.storedUnit(movement.unitId)
        val pkg = requireNotNull(pack) { "движение ссылается на пачку, которой нет: ${movement.packageId}" }
            .toRef(vocabulary)
        return when (movement.kind) {
            StockMovementStorageEntity.Kind.RECEIPT -> StockMovement.Receipt(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.RECOUNT -> StockMovement.Recount(
                id = movement.id,
                pkg = pkg,
                before = quantity(movement.beforeAmount, unit),
                after = quantity(movement.afterAmount, unit),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.DISPOSAL -> StockMovement.Disposal(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                reason = requireNotNull(movement.reason) { "у утилизации названа причина" },
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.REMOTE_CHANGE -> StockMovement.RemoteChange(
                id = movement.id,
                pkg = pkg,
                delta = BigDecimal(requireNotNull(movement.delta) { "у чужого изменения есть разница" }),
                unit = unit,
                observedAt = movement.observedAt,
                occurredAt = movement.occurredAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.ACCESS_LOSS -> StockMovement.AccessLoss(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                observedAt = movement.observedAt,
                occurredAt = movement.occurredAt,
                note = movement.note
            )
        }
    }

    private fun quantity(text: String?, unit: QuantityUnit) =
        storedQuantity(requireNotNull(text) { "у движения вида ${movement.kind} записано количество" }, unit)

    private fun moment() =
        requireNotNull(movement.occurredAt) { "движение вида ${movement.kind} знает, когда случилось" }
}
