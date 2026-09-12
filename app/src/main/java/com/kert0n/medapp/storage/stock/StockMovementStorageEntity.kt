package com.kert0n.medapp.storage.stock

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Одна строка на движение остатка — запись о **пачке**: что с ней стало и когда (PLAN D7, F1).
 * Аптечки здесь нет: где коробка лежит, знает она сама, а «сколько истрачено» от места не зависит.
 *
 * Ключ на пачку — `RESTRICT`: строка движения описывает пачку и без неё не читается, поэтому
 * порядок удаления называет тот, кто удаляет, а не схема (PLAN F2).
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
    val note: String? = null
) {
    /** Дискриминатор вида: варианты движения читаются целиком в `StockMovement.kt` (PLAN D7). */
    enum class Kind { RECEIPT, RECOUNT, DISPOSAL, REMOTE_CHANGE, ACCESS_LOSS }
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
        is StockMovement.Receipt -> common.with(amount = amount.toStorageAmount())
        is StockMovement.Recount -> common.with(
            beforeAmount = before.toStorageAmount(),
            afterAmount = after.toStorageAmount()
        )
        is StockMovement.Disposal -> common.with(amount = amount.toStorageAmount(), reason = reason)
        is StockMovement.RemoteChange -> common.with(delta = delta.toPlainString())
        is StockMovement.AccessLoss -> common.with(amount = amount.toStorageAmount())
    }
}

private fun StockMovement.kindOf(): StockMovementStorageEntity.Kind = when (this) {
    is StockMovement.Receipt -> StockMovementStorageEntity.Kind.RECEIPT
    is StockMovement.Recount -> StockMovementStorageEntity.Kind.RECOUNT
    is StockMovement.Disposal -> StockMovementStorageEntity.Kind.DISPOSAL
    is StockMovement.RemoteChange -> StockMovementStorageEntity.Kind.REMOTE_CHANGE
    is StockMovement.AccessLoss -> StockMovementStorageEntity.Kind.ACCESS_LOSS
}

private fun StockMovementStorageEntity.with(
    amount: String? = null,
    beforeAmount: String? = null,
    afterAmount: String? = null,
    delta: String? = null,
    reason: StockMovement.Disposal.Reason? = null
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
    note = note
)
