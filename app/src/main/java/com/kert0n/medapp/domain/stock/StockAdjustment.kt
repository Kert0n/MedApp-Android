package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.domain.value.requireOptionalText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

const val ADJUSTMENT_NOTE_MAX_LENGTH = 200

/**
 * Изменение остатка, которое не является приёмом.
 *
 * Без движений история «всё добавленное минус оставшееся» (ТЗ 4.1.1.10.2) не сходится: перенос
 * между аптечками выглядел бы расходом, а утилизация просрочки — приёмом.
 *
 * **Вид события — это тип, а не поле.** Смысл полей задаёт вид, поэтому у каждого вида свой
 * конструктор и ровно те поля, которые для него осмысленны. Отсюда исчезают проверки сочетаний:
 * у непереноса нет аптечек переноса, у прихода нет способа записать отрицательное количество,
 * у собственного действия момент обязателен по типу. Утилизация с приходом не отвергается —
 * её **нельзя выразить**.
 *
 * Каждый вид лежит в своём файле этого пакета: их семь, и по одному в файле их видно списком
 * каталога, а не поиском внутри одного длинного объявления.
 *
 * Дискриминатор вида живёт в колонке `stock_adjustments.kind` и её конвертере (PLAN F1), а не
 * здесь: это представление хранения, а не доменное понятие.
 */
sealed interface StockAdjustment {

    val id: Uuid
    val packageId: Uuid

    /** Знаковая и **вычисляемая**: сторону задаёт вид события, а не тот, кто его записывает. */
    val delta: BigDecimal

    /** Единица НА МОМЕНТ СОБЫТИЯ: переименование и смена единицы прошлое не переписывают. */
    val unitId: Uuid

    /** Где изменился остаток. У переноса следует из направления. */
    val medKitId: Uuid

    /** `null` только у того, что сделали не мы: чужое изменение и утрата доступа. */
    val occurredAt: Instant?

    val observedAt: Instant
    val operationId: Uuid?
    val note: String?
}

internal fun requireNote(note: String?) {
    requireOptionalText(note, ADJUSTMENT_NOTE_MAX_LENGTH, "StockAdjustment.note")
}
