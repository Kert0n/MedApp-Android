package com.kert0n.medapp.domain.model.stock

import com.kert0n.medapp.domain.model.value.Quantity
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Пересчитали и увидели другое число.
 *
 * Принимает оба остатка, а не дельту: знак получается сам, единица берётся из величин и
 * противоречить им не может. Вместе с [StockRemoteChange] это единственные виды, у которых
 * сторона заранее неизвестна — пересчёт находит и больше, и меньше.
 */
data class StockCorrection(
    override val id: Uuid,
    override val packageId: Uuid,
    val from: Quantity,
    val to: Quantity,
    override val medKitId: Uuid,
    override val occurredAt: Instant,
    override val observedAt: Instant,
    override val operationId: Uuid? = null,
    override val note: String? = null
) : StockAdjustment {
    init {
        require(from.unitId == to.unitId) { "пересчёт не меняет единицу" }
        requireNote(note)
    }
    override val delta: BigDecimal get() = to.amount - from.amount
    override val unitId: Uuid get() = to.unitId
}
