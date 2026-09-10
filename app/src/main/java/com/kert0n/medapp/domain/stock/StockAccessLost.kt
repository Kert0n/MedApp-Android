package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.domain.value.Quantity
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/** Пачка перестала быть видимой: из учёта уходит весь остаток, что мы последним видели. */
data class StockAccessLost(
    override val id: Uuid,
    override val packageId: Uuid,
    val amount: Quantity,
    override val medKitId: Uuid,
    override val observedAt: Instant,
    override val occurredAt: Instant? = null,
    override val operationId: Uuid? = null,
    override val note: String? = null
) : StockAdjustment {
    init { requireNote(note) }
    override val delta: BigDecimal get() = amount.amount.negate()
    override val unitId: Uuid get() = amount.unitId
}
