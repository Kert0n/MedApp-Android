package com.kert0n.medapp.storage.stock

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Одна строка на движение остатка. У переноса обе аптечки и одно количество, поэтому концы
 * переноса не расходятся и знак в отчёте не переворачивается (PLAN D7, F1).
 *
 * Ключ на пачку — `RESTRICT`: строка упаковки не удаляется, она архивируется, а ограничение
 * защищает историю от случайного каскада. Аптечки движения — те же ключи: движение случилось в
 * аптечке, и без неё его не прочитать. Пачка и аптечки — колонками; в домен их собирает
 * `StockMovementStorageRow` связями.
 */
@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = PackageStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MedKitStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["med_kit_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MedKitStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_med_kit_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MedKitStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_med_kit_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["package_id", "observed_at"]),
        Index("med_kit_id"),
        Index("source_med_kit_id"),
        Index("target_med_kit_id")
    ]
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
}

fun StockMovement.toStorageEntity(): StockMovementStorageEntity {
    val common = StockMovementStorageEntity(
        id = id,
        packageId = pkg.id,
        kind = kindOf(),
        unitId = unit.id,
        observedAt = observedAt,
        occurredAt = occurredAt,
        note = note
    )
    return when (this) {
        is StockMovement.Receipt -> common.with(amount = amount.toStorageAmount(), medKitId = medKit.id)
        is StockMovement.Recount -> common.with(
            beforeAmount = before.toStorageAmount(),
            afterAmount = after.toStorageAmount(),
            medKitId = medKit.id
        )
        is StockMovement.Disposal -> common.with(
            amount = amount.toStorageAmount(),
            reason = reason,
            medKitId = medKit.id
        )
        is StockMovement.Transfer -> common.with(
            amount = amount.toStorageAmount(),
            sourceMedKitId = source.id,
            targetMedKitId = target.id
        )
        is StockMovement.RemoteChange -> common.with(
            delta = delta.toPlainString(),
            medKitId = medKit.id
        )
        is StockMovement.AccessLoss -> common.with(
            amount = amount.toStorageAmount(),
            medKitId = medKit.id
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
