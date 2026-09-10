package com.kert0n.medapp.domain.model.stock

import com.kert0n.medapp.domain.model.value.QUANTITY_MAX_INTEGER_DIGITS
import com.kert0n.medapp.domain.model.value.QUANTITY_SCALE
import com.kert0n.medapp.domain.model.value.requireDecimalWithinLimits
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Изменилось на сервере, и это не мы.
 *
 * Записывает **остаток** расхождения, а не всё расхождение целиком:
 * `remoteDelta = serverQuantity − (lastKnownQuantity + Σ наши установленные изменения)`.
 * Иначе одно наше списание попало бы в отчёт дважды — записью приёма и изменением серверного
 * числа. Причина не выдумывается: сервер истории не хранит и сказать, что это было, не может.
 *
 * Дельта знаковая и приходит из формулы, а не из величины; момент известен лишь тот, когда мы
 * это заметили (PLAN D7).
 */
data class StockRemoteChange(
    override val id: Uuid,
    override val packageId: Uuid,
    override val delta: BigDecimal,
    override val unitId: Uuid,
    override val medKitId: Uuid,
    override val observedAt: Instant,
    override val occurredAt: Instant? = null,
    override val operationId: Uuid? = null,
    override val note: String? = null
) : StockAdjustment {
    init {
        requireDecimalWithinLimits(
            amount = delta,
            field = "StockRemoteChange.delta",
            maxScale = QUANTITY_SCALE,
            maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
        )
        requireNote(note)
    }
}
