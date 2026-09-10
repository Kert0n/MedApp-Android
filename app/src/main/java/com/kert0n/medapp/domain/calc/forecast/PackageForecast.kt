package com.kert0n.medapp.domain.calc.forecast

import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Прогноз по одной упаковке на названный момент.
 *
 * [remaining] — `null`, когда исход операции по этой пачке не установлен: **выдуманный остаток не
 * рисуется** (PLAN D4, H1). Ноль и «неизвестно» — разные ответы, и подменять второй первым
 * значило бы обещать отсутствие лекарства, которого мы не проверяли.
 *
 * [expired] помечает, а не обнуляет: просрочка ничего не списывает, и просроченная пачка
 * остаётся источником (PLAN D3).
 *
 * [reservedByOthers] показывается отдельно, а не вычитается из [remaining]: физически таблетки в
 * пачке есть, просто заявлены другими людьми — смешивать «сколько лежит» и «сколько моё» нельзя.
 */
data class PackageForecast(
    val packageId: Uuid,
    val at: Instant,
    val remaining: Quantity?,
    val reservedByOthers: Quantity,
    val expired: Boolean,
    val requiresRecount: Boolean
) {
    init {
        require(remaining != null || requiresRecount) {
            "остаток неизвестен только при требуемой сверке"
        }
    }
}
