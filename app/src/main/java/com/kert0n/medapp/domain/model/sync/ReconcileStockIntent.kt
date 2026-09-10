package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Ручная сверка: человек сам назвал фактический остаток вместо того, чтобы ждать разрешения
 * неустановленных операций (PLAN E3).
 *
 * [throughSequence] — до какого номера очереди сверка отвечает за прошлое. Прежние намерения по
 * этой пачке в проекцию больше не входят: их исход остаётся неизвестным, но остаток теперь
 * назван человеком, и повторно вычитать неустановленный расход нельзя. Более новые намерения
 * применяются поверх названного числа.
 */
data class ReconcileStockIntent(
    val packageId: Uuid,
    val actual: Quantity,
    val throughSequence: Long
) : SyncIntent {
    init {
        require(throughSequence >= 0) { "номер в очереди не бывает отрицательным" }
    }
}
