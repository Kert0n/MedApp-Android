package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.domain.value.Quantity
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/** Выбросили: просрочка, порча. Списывается названное количество. */
data class StockDisposal(
    override val id: Uuid,
    override val packageId: Uuid,
    val amount: Quantity,
    override val medKitId: Uuid,
    override val occurredAt: Instant,
    override val observedAt: Instant,
    override val operationId: Uuid? = null,
    override val note: String? = null
) : StockAdjustment {
    init { requireNote(note) }
    override val delta: BigDecimal get() = amount.amount.negate()
    override val unitId: Uuid get() = amount.unitId
}
