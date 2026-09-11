package com.kert0n.medapp.storage.stock

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageRow
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.storedUnit
import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * Движение вместе с пачкой и аптечками, которые оно называет. Домен держит их объектами; Room
 * читает связи той же транзакцией и по одному запросу на связь на всю выборку — история из ста
 * строк грузит пачки одним запросом, а не ста.
 */
class StockMovementStorageRow(
    @Embedded val movement: StockMovementStorageEntity,
    @Relation(entity = PackageStorageEntity::class, parentColumn = "package_id", entityColumn = "id")
    val pack: PackageStorageRow? = null,
    @Relation(parentColumn = "med_kit_id", entityColumn = "id")
    val medKit: MedKitStorageEntity? = null,
    @Relation(parentColumn = "source_med_kit_id", entityColumn = "id")
    val source: MedKitStorageEntity? = null,
    @Relation(parentColumn = "target_med_kit_id", entityColumn = "id")
    val target: MedKitStorageEntity? = null
) {
    fun toDomain(vocabulary: Vocabulary): StockMovement {
        val unit = vocabulary.storedUnit(movement.unitId)
        val pkg = requireNotNull(pack) { "движение ссылается на пачку, которой нет: ${movement.packageId}" }
            .toDomain(vocabulary)
        val kind = movement.kind
        return when (kind) {
            StockMovementStorageEntity.Kind.RECEIPT -> StockMovement.Receipt(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                medKit = medKit(medKit, movement.medKitId),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.RECOUNT -> StockMovement.Recount(
                id = movement.id,
                pkg = pkg,
                before = quantity(movement.beforeAmount, unit),
                after = quantity(movement.afterAmount, unit),
                medKit = medKit(medKit, movement.medKitId),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.DISPOSAL -> StockMovement.Disposal(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                reason = requireNotNull(movement.reason) { "у утилизации названа причина" },
                medKit = medKit(medKit, movement.medKitId),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.TRANSFER -> StockMovement.Transfer(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                source = medKit(source, movement.sourceMedKitId),
                target = medKit(target, movement.targetMedKitId),
                occurredAt = moment(),
                observedAt = movement.observedAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.REMOTE_CHANGE -> StockMovement.RemoteChange(
                id = movement.id,
                pkg = pkg,
                delta = BigDecimal(requireNotNull(movement.delta) { "у чужого изменения есть разница" }),
                unit = unit,
                medKit = medKit(medKit, movement.medKitId),
                observedAt = movement.observedAt,
                occurredAt = movement.occurredAt,
                note = movement.note
            )
            StockMovementStorageEntity.Kind.ACCESS_LOSS -> StockMovement.AccessLoss(
                id = movement.id,
                pkg = pkg,
                amount = quantity(movement.amount, unit),
                medKit = medKit(medKit, movement.medKitId),
                observedAt = movement.observedAt,
                occurredAt = movement.occurredAt,
                note = movement.note
            )
        }
    }

    private fun quantity(text: String?, unit: QuantityUnit) =
        storedQuantity(requireNotNull(text) { "у движения вида ${movement.kind} записано количество" }, unit)

    private fun medKit(read: MedKitStorageEntity?, id: Uuid?): MedKit =
        requireNotNull(read) {
            "движение вида ${movement.kind} называет аптечку, которой нет: $id"
        }.toDomain()

    private fun moment() =
        requireNotNull(movement.occurredAt) { "движение вида ${movement.kind} знает, когда случилось" }
}
