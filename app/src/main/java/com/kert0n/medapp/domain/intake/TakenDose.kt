package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Состоявшийся приём: сколько, из какой пачки, в какой аптечке и когда — четыре поля, которые
 * бывают только вместе. Пачка может отличаться от плановой, и расход относится к фактической
 * (PLAN D5). Аптечка и единица записаны на момент события: перенос пачки прошлые отчёты не
 * переписывает (D6).
 */
data class TakenDose(
    val packageId: Uuid,
    val medKitId: Uuid,
    val amount: Quantity,
    val at: Instant
) {
    init {
        require(!amount.isZero) { "принятый ноль — это пропуск, а не приём" }
    }
}
