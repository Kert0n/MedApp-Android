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
 * **Свершившийся факт, поэтому величина, а не сущность:** другая дельта означает другое событие,
 * а не изменённое. Но смысл полей задаёт вид, и произвольный их набор события не выражает.
 * Поэтому конструктор приватный, а собрать движение можно только фабрикой своего вида: у неё в
 * подписи ровно то, что для этого вида осмысленно, и знак с аптечками получаются сами.
 * Утилизация с приходом или перенос, «уехавший» из аптечки назначения, здесь не отвергаются —
 * их **нельзя записать**.
 *
 * [REMOTE_CHANGE][AdjustmentKind.REMOTE_CHANGE] записывает **остаток** расхождения, а не всё
 * расхождение целиком:
 * `remoteDelta = serverQuantity − (lastKnownQuantity + Σ наши установленные изменения)`.
 * Иначе одно наше списание попало бы в отчёт дважды — записью приёма и изменением серверного
 * числа. Причина при этом не выдумывается: сервер истории не хранит и сказать, что это было,
 * не может (PLAN D7).
 */
@ConsistentCopyVisibility
data class StockAdjustment private constructor(
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
            // Событие происходит там, где остаток изменился: уехало — из аптечки-источника,
            // приехало — в аптечку назначения. Несогласованное поле сделало бы отчёт по аптечке
            // неверным, не нарушив при этом ни одного другого правила.
            val where = if (kind == AdjustmentKind.TRANSFER_OUT) fromMedKitId else toMedKitId
            require(medKitId == where) {
                "$kind произошёл в $where, а записан в $medKitId"
            }
        } else {
            require(fromMedKitId == null && toMedKitId == null) {
                "аптечки переноса заполняются только у переносов"
            }
        }

        // Второй рубеж к фабрикам: направление задано видом движения, и запись против него ломает
        // историю тихо — отрицательный приход и положительная утилизация сходятся в сумме, но
        // означают противоположное написанному (PLAN D7, ТЗ 4.1.1.10.2).
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

    companion object {

        /** Пачка заведена: весь её начальный остаток — приход. */
        fun initial(
            id: Uuid,
            packageId: Uuid,
            amount: Quantity,
            medKitId: Uuid,
            occurredAt: Instant,
            observedAt: Instant,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.INITIAL, amount.amount, amount.unitId,
            medKitId, null, null, occurredAt, observedAt, operationId, note
        )

        /**
         * Пересчитали и увидели другое число.
         *
         * Принимает оба остатка, а не дельту: знак получается сам, единица берётся из величин и
         * не может им противоречить. Пересчёт находит и больше, и меньше — это единственный
         * вид движения, у которого сторона заранее неизвестна, вместе с [remoteChange].
         */
        fun correction(
            id: Uuid,
            packageId: Uuid,
            from: Quantity,
            to: Quantity,
            medKitId: Uuid,
            occurredAt: Instant,
            observedAt: Instant,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment {
            require(from.unitId == to.unitId) { "пересчёт не меняет единицу" }
            return of(
                id, packageId, AdjustmentKind.CORRECTION, to.amount - from.amount, to.unitId,
                medKitId, null, null, occurredAt, observedAt, operationId, note
            )
        }

        /** Выбросили: просрочка, порча. Списывается названное количество. */
        fun disposal(
            id: Uuid,
            packageId: Uuid,
            amount: Quantity,
            medKitId: Uuid,
            occurredAt: Instant,
            observedAt: Instant,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.DISPOSAL, amount.amount.negate(), amount.unitId,
            medKitId, null, null, occurredAt, observedAt, operationId, note
        )

        /** Уехала: событие произошло в аптечке-источнике, поэтому `medKitId` — это [from]. */
        fun transferOut(
            id: Uuid,
            packageId: Uuid,
            amount: Quantity,
            from: Uuid,
            to: Uuid,
            occurredAt: Instant,
            observedAt: Instant,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.TRANSFER_OUT, amount.amount.negate(), amount.unitId,
            from, from, to, occurredAt, observedAt, operationId, note
        )

        /** Приехала: событие произошло в аптечке назначения, поэтому `medKitId` — это [to]. */
        fun transferIn(
            id: Uuid,
            packageId: Uuid,
            amount: Quantity,
            from: Uuid,
            to: Uuid,
            occurredAt: Instant,
            observedAt: Instant,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.TRANSFER_IN, amount.amount, amount.unitId,
            to, from, to, occurredAt, observedAt, operationId, note
        )

        /**
         * Изменилось на сервере, и это не мы.
         *
         * Дельта знаковая и приходит из формулы D7, а не из величины: она уже остаток
         * расхождения. Момент неизвестен — чужое действие замечено задним числом.
         */
        fun remoteChange(
            id: Uuid,
            packageId: Uuid,
            delta: BigDecimal,
            unitId: Uuid,
            medKitId: Uuid,
            observedAt: Instant,
            occurredAt: Instant? = null,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.REMOTE_CHANGE, delta, unitId,
            medKitId, null, null, occurredAt, observedAt, operationId, note
        )

        /** Пачка перестала быть видимой: из учёта уходит весь остаток, что мы последним видели. */
        fun accessLost(
            id: Uuid,
            packageId: Uuid,
            amount: Quantity,
            medKitId: Uuid,
            observedAt: Instant,
            occurredAt: Instant? = null,
            operationId: Uuid? = null,
            note: String? = null
        ): StockAdjustment = of(
            id, packageId, AdjustmentKind.ACCESS_LOST, amount.amount.negate(), amount.unitId,
            medKitId, null, null, occurredAt, observedAt, operationId, note
        )

        @Suppress("LongParameterList")
        private fun of(
            id: Uuid,
            packageId: Uuid,
            kind: AdjustmentKind,
            delta: BigDecimal,
            unitId: Uuid,
            medKitId: Uuid?,
            fromMedKitId: Uuid?,
            toMedKitId: Uuid?,
            occurredAt: Instant?,
            observedAt: Instant,
            operationId: Uuid?,
            note: String?
        ): StockAdjustment = StockAdjustment(
            id = id,
            packageId = packageId,
            kind = kind,
            delta = delta,
            unitId = unitId,
            medKitId = medKitId,
            fromMedKitId = fromMedKitId,
            toMedKitId = toMedKitId,
            occurredAt = occurredAt,
            observedAt = observedAt,
            operationId = operationId,
            note = note
        )
    }
}
