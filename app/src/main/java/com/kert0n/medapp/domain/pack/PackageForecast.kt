package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Прогноз по одной пачке на момент [at]: сколько останется к этому моменту. Просрочка помечается
 * [expired], а не обнуляет остаток (PLAN D3); чужие брони показываются отдельно — таблетки в
 * пачке лежат, просто заявлены другими.
 */
data class PackageForecast(
    val packageId: Uuid,
    val at: Instant,
    val remaining: Quantity,
    val reservedByOthers: Quantity,
    val expired: Boolean
) {

    companion object {
        /** Горизонт прогноза: не дальше трёх календарных месяцев (ТЗ 4.1.1.10). */
        const val MAX_MONTHS = 3L
    }
}
