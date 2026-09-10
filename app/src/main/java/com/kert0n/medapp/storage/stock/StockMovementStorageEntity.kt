package com.kert0n.medapp.storage.stock

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.toStorageAmount
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Одна строка на движение остатка. У переноса обе аптечки и одно количество, поэтому концы
 * переноса не расходятся и знак в отчёте не переворачивается (PLAN D7, F1).
 *
 * Ключ на пачку — `RESTRICT`: строка упаковки не удаляется, она архивируется, а ограничение
 * защищает историю от случайного каскада.
 */
@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = PackageStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["package_id", "observed_at"])]
)
class StockMovementStorageEntity(
    @PrimaryKey val id: Uuid,
    @ColumnInfo(name = "package_id") val packageId: Uuid,
    val kind: Kind,
    @ColumnInfo(name = "unit_id") val unitId: Uuid,
    @ColumnInfo(name = "observed_at") val observedAt: Instant,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant? = null,
    val amount: String? = null,
    @ColumnInfo(name = "before_amount") val beforeAmount: String? = null,
    @ColumnInfo(name = "after_amount") val afterAmount: String? = null,
    val delta: String? = null,
    val reason: StockMovement.Disposal.Reason? = null,
    @ColumnInfo(name = "med_kit_id") val medKitId: Uuid? = null,
    @ColumnInfo(name = "source_med_kit_id") val sourceMedKitId: Uuid? = null,
    @ColumnInfo(name = "target_med_kit_id") val targetMedKitId: Uuid? = null,
    val note: String? = null
) {
    /** Дискриминатор вида: варианты движения читаются целиком в `StockMovement.kt` (PLAN D7). */
    enum class Kind { RECEIPT, RECOUNT, DISPOSAL, TRANSFER, REMOTE_CHANGE, ACCESS_LOSS }

    fun toDomain(): StockMovement = when (kind) {
        Kind.RECEIPT -> StockMovement.Receipt(
            id = id,
            packageId = packageId,
            amount = quantity(amount),
            medKitId = medKit(),
            occurredAt = moment(),
            observedAt = observedAt,
            note = note
        )
        Kind.RECOUNT -> StockMovement.Recount(
            id = id,
            packageId = packageId,
            before = quantity(beforeAmount),
            after = quantity(afterAmount),
            medKitId = medKit(),
            occurredAt = moment(),
            observedAt = observedAt,
            note = note
        )
        Kind.DISPOSAL -> StockMovement.Disposal(
            id = id,
            packageId = packageId,
            amount = quantity(amount),
            reason = requireNotNull(reason) { "у утилизации названа причина" },
            medKitId = medKit(),
            occurredAt = moment(),
            observedAt = observedAt,
            note = note
        )
        Kind.TRANSFER -> StockMovement.Transfer(
            id = id,
            packageId = packageId,
            amount = quantity(amount),
            sourceMedKitId = requireNotNull(sourceMedKitId) { "у переноса есть откуда" },
            targetMedKitId = requireNotNull(targetMedKitId) { "у переноса есть куда" },
            occurredAt = moment(),
            observedAt = observedAt,
            note = note
        )
        Kind.REMOTE_CHANGE -> StockMovement.RemoteChange(
            id = id,
            packageId = packageId,
            delta = BigDecimal(requireNotNull(delta) { "у чужого изменения есть разница" }),
            unitId = unitId,
            medKitId = medKit(),
            observedAt = observedAt,
            occurredAt = occurredAt,
            note = note
        )
        Kind.ACCESS_LOSS -> StockMovement.AccessLoss(
            id = id,
            packageId = packageId,
            amount = quantity(amount),
            medKitId = medKit(),
            observedAt = observedAt,
            occurredAt = occurredAt,
            note = note
        )
    }

    private fun quantity(text: String?) =
        storedQuantity(requireNotNull(text) { "у движения вида $kind записано количество" }, unitId)

    private fun medKit() = requireNotNull(medKitId) { "движение вида $kind называет свою аптечку" }

    private fun moment() =
        requireNotNull(occurredAt) { "движение вида $kind знает, когда случилось" }
}

fun StockMovement.toStorageEntity(): StockMovementStorageEntity {
    val common = StockMovementStorageEntity(
        id = id,
        packageId = packageId,
        kind = kindOf(),
        unitId = unitId,
        observedAt = observedAt,
        occurredAt = occurredAt,
        note = note
    )
    return when (this) {
        is StockMovement.Receipt -> common.with(amount = amount.toStorageAmount(), medKitId = medKitId)
        is StockMovement.Recount -> common.with(
            beforeAmount = before.toStorageAmount(),
            afterAmount = after.toStorageAmount(),
            medKitId = medKitId
        )
        is StockMovement.Disposal -> common.with(
            amount = amount.toStorageAmount(),
            reason = reason,
            medKitId = medKitId
        )
        is StockMovement.Transfer -> common.with(
            amount = amount.toStorageAmount(),
            sourceMedKitId = sourceMedKitId,
            targetMedKitId = targetMedKitId
        )
        is StockMovement.RemoteChange -> common.with(
            delta = delta.toPlainString(),
            medKitId = medKitId
        )
        is StockMovement.AccessLoss -> common.with(
            amount = amount.toStorageAmount(),
            medKitId = medKitId
        )
    }
}

private fun StockMovement.kindOf(): StockMovementStorageEntity.Kind = when (this) {
    is StockMovement.Receipt -> StockMovementStorageEntity.Kind.RECEIPT
    is StockMovement.Recount -> StockMovementStorageEntity.Kind.RECOUNT
    is StockMovement.Disposal -> StockMovementStorageEntity.Kind.DISPOSAL
    is StockMovement.Transfer -> StockMovementStorageEntity.Kind.TRANSFER
    is StockMovement.RemoteChange -> StockMovementStorageEntity.Kind.REMOTE_CHANGE
    is StockMovement.AccessLoss -> StockMovementStorageEntity.Kind.ACCESS_LOSS
}

private fun StockMovementStorageEntity.with(
    amount: String? = null,
    beforeAmount: String? = null,
    afterAmount: String? = null,
    delta: String? = null,
    reason: StockMovement.Disposal.Reason? = null,
    medKitId: Uuid? = null,
    sourceMedKitId: Uuid? = null,
    targetMedKitId: Uuid? = null
) = StockMovementStorageEntity(
    id = id,
    packageId = packageId,
    kind = kind,
    unitId = unitId,
    observedAt = observedAt,
    occurredAt = occurredAt,
    amount = amount,
    beforeAmount = beforeAmount,
    afterAmount = afterAmount,
    delta = delta,
    reason = reason,
    medKitId = medKitId,
    sourceMedKitId = sourceMedKitId,
    targetMedKitId = targetMedKitId,
    note = note
)
