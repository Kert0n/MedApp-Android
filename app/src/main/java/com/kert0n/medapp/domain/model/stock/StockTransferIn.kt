package com.kert0n.medapp.domain.model.stock

import com.kert0n.medapp.domain.model.value.Quantity
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/** Приехала: событие произошло в аптечке назначения, поэтому [medKitId] — это [to]. */
data class StockTransferIn(
    override val id: Uuid,
    override val packageId: Uuid,
    val amount: Quantity,
    val from: Uuid,
    val to: Uuid,
    override val occurredAt: Instant,
    override val observedAt: Instant,
    override val operationId: Uuid? = null,
    override val note: String? = null
) : StockAdjustment {
    init {
        require(from != to) { "перенос внутри одной аптечки остаток не меняет" }
        requireNote(note)
    }
    override val delta: BigDecimal get() = amount.amount
    override val unitId: Uuid get() = amount.unitId
    override val medKitId: Uuid get() = to
}
