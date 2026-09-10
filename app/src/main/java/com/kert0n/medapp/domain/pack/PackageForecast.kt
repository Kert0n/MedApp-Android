package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Прогноз по одной пачке на момент [at]: количество к этому моменту или «неизвестно», если оно
 * неизвестно и сейчас. Просрочка помечается [expired], а не обнуляет остаток (PLAN D3); чужие
 * брони показываются отдельно — таблетки в пачке лежат, просто заявлены другими.
 */
data class PackageForecast(
    val packageId: Uuid,
    val at: Instant,
    val amount: EffectiveAmount,
    val reservedByOthers: Quantity,
    val expired: Boolean
) {

    val remaining: Quantity? get() = amount.quantityOrNull

    val requiresRecount: Boolean get() = amount == EffectiveAmount.Unknown

    companion object {
        /** Горизонт прогноза: не дальше трёх календарных месяцев (ТЗ 4.1.1.10). */
        const val MAX_MONTHS = 3L
    }
}
