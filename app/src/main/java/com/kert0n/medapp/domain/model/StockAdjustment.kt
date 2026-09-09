package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

const val ADJUSTMENT_NOTE_MAX_LENGTH = 200

enum class AdjustmentKind {
    INITIAL,        // пачка заведена
    CORRECTION,     // пересчитали и увидели другое число
    DISPOSAL,       // выбросили: просрочка, порча
    TRANSFER_IN,    // приехала из другой аптечки
    TRANSFER_OUT,   // уехала
    REMOTE_CHANGE,  // изменилось на сервере, и это не мы
    ACCESS_LOST     // пачка перестала быть видимой
}

/**
 * Изменение остатка, которое не является приёмом.
 *
 * Без движений история «всё добавленное минус оставшееся» (ТЗ 4.1.1.10.2) не сходится: перенос
 * между аптечками выглядел бы расходом, а утилизация просрочки — приёмом.
 *
 * [REMOTE_CHANGE][AdjustmentKind.REMOTE_CHANGE] записывает **остаток** расхождения, а не всё
 * расхождение целиком:
 * `remoteDelta = serverQuantity − (lastKnownQuantity + Σ наши установленные изменения)`.
 * Иначе одно наше списание попало бы в отчёт дважды — записью приёма и изменением серверного
 * числа. Причина при этом не выдумывается: сервер истории не хранит и сказать, что это было,
 * не может (PLAN D7).
 */
data class StockAdjustment(
    val id: Uuid,
    val packageId: Uuid,
    val kind: AdjustmentKind,
    val delta: BigDecimal,             // знаковая
    val unitId: Uuid,                  // НА МОМЕНТ СОБЫТИЯ
    val medKitId: Uuid?,               // где произошло
    val fromMedKitId: Uuid?,           // у переносов
    val toMedKitId: Uuid?,
    val occurredAt: Instant?,          // null — момент неизвестен (чужое изменение)
    val observedAt: Instant,           // когда мы это увидели
    val operationId: Uuid?,            // какая операция очереди это породила
    val note: String?
) {

    init {
        // Знак у движения бывает любой, поэтому неотрицательности здесь нет — только границы.
        requireDecimalWithinLimits(delta, "StockAdjustment.delta")
        requireOptionalText(note, ADJUSTMENT_NOTE_MAX_LENGTH, "StockAdjustment.note")

        val transfer = kind == AdjustmentKind.TRANSFER_IN || kind == AdjustmentKind.TRANSFER_OUT
        if (transfer) {
            require(fromMedKitId != null && toMedKitId != null) {
                "перенос без обеих аптечек нельзя ни показать, ни отличить от расхода"
            }
            require(fromMedKitId != toMedKitId) {
                "перенос внутри одной аптечки остаток не меняет"
            }
        } else {
            require(fromMedKitId == null && toMedKitId == null) {
                "аптечки переноса заполняются только у переносов"
            }
        }

        // Направление задано видом движения, и запись против него ломает историю тихо:
        // отрицательный приход и положительная утилизация сходятся в сумме, но означают
        // противоположное тому, что написано в их виде (PLAN D7, ТЗ 4.1.1.10.2).
        when (kind) {
            AdjustmentKind.INITIAL, AdjustmentKind.TRANSFER_IN ->
                require(delta.signum() >= 0) { "приход не бывает отрицательным: $kind" }

            AdjustmentKind.DISPOSAL, AdjustmentKind.TRANSFER_OUT, AdjustmentKind.ACCESS_LOST ->
                require(delta.signum() <= 0) { "расход не бывает положительным: $kind" }

            // Пересчёт находит и больше, и меньше; чужое изменение идёт в обе стороны.
            AdjustmentKind.CORRECTION, AdjustmentKind.REMOTE_CHANGE -> Unit
        }

        // Момент своего действия нам известен всегда; неизвестен он только у того, что сделали
        // не мы: чужое изменение на сервере и утрата доступа замечены задним числом (PLAN D7).
        if (occurredAt == null) {
            require(
                kind == AdjustmentKind.REMOTE_CHANGE || kind == AdjustmentKind.ACCESS_LOST
            ) {
                "у собственного изменения момент известен: $kind"
            }
        }
    }
}
