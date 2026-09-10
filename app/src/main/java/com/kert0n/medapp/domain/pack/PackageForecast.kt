package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Прогноз по одной упаковке на названный момент.
 *
 * [amount] — оценка количества на этот момент, и она же отвечает за «неизвестно». Пара «число
 * плюс признак» здесь была ровно тем, против чего заведён [EffectiveAmount]: два поля, сшитые
 * `require`, позволяли собрать ноль там, где остаток на самом деле неизвестен. Теперь у
 * неизвестного случая числа просто нет, а с ним приезжают и операции, из-за которых нужна
 * сверка, — раньше они терялись при подъёме в прогноз (PLAN D4, E3).
 *
 * [expired] помечает, а не обнуляет: просрочка ничего не списывает, и просроченная пачка
 * остаётся источником (PLAN D3).
 *
 * [reservedByOthers] показывается отдельно, а не вычитается из остатка: физически таблетки в
 * пачке есть, просто заявлены другими людьми — смешивать «сколько лежит» и «сколько моё» нельзя.
 */
data class PackageForecast(
    val packageId: Uuid,
    val at: Instant,
    val amount: EffectiveAmount,
    val reservedByOthers: Quantity,
    val expired: Boolean
) {

    /** `null` при требуемой сверке: выдуманный остаток не рисуется. */
    val remaining: Quantity? get() = amount.quantityOrNull

    val requiresRecount: Boolean get() = amount is EffectiveAmount.NeedsRecount
}
