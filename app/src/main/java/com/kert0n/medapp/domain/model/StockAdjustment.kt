package com.kert0n.medapp.domain.model

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
 * конструктор и ровно те поля, которые для него осмысленны. Отсюда исчезают все проверки
 * сочетаний: у непереноса нет аптечек переноса, у прихода нет способа записать отрицательное
 * количество, у собственного действия момент обязателен. Утилизация с приходом не отвергается —
 * её **нельзя выразить**.
 *
 * [delta] знаковая и вычисляется, а не хранится: сторону задаёт вид события.
 *
 * [medKitId] — где изменился остаток. У переноса это следует из направления: уехало из
 * аптечки-источника, приехало в аптечку назначения.
 */
sealed interface StockAdjustment {

    val id: Uuid
    val packageId: Uuid
    val delta: BigDecimal
    val unitId: Uuid
    val medKitId: Uuid
    val occurredAt: Instant?
    val observedAt: Instant
    val operationId: Uuid?
    val note: String?

    /** Пачка заведена: весь её начальный остаток — приход. */
    data class Initial(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        override val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init { requireNote(note) }
        override val delta: BigDecimal get() = amount.amount
        override val unitId: Uuid get() = amount.unitId
    }

    /**
     * Пересчитали и увидели другое число.
     *
     * Принимает оба остатка, а не дельту: знак получается сам, единица берётся из величин и
     * противоречить им не может. Вместе с [RemoteChange] это единственные виды, у которых сторона
     * заранее неизвестна — пересчёт находит и больше, и меньше.
     */
    data class Correction(
        override val id: Uuid,
        override val packageId: Uuid,
        val from: Quantity,
        val to: Quantity,
        override val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init {
            require(from.unitId == to.unitId) { "пересчёт не меняет единицу" }
            requireNote(note)
        }
        override val delta: BigDecimal get() = to.amount - from.amount
        override val unitId: Uuid get() = to.unitId
    }

    /** Выбросили: просрочка, порча. Списывается названное количество. */
    data class Disposal(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        override val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init { requireNote(note) }
        override val delta: BigDecimal get() = amount.amount.negate()
        override val unitId: Uuid get() = amount.unitId
    }

    /** Уехала: событие произошло в аптечке-источнике, поэтому [medKitId] — это [from]. */
    data class TransferOut(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val from: Uuid,
        val to: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init {
            require(from != to) { "перенос внутри одной аптечки остаток не меняет" }
            requireNote(note)
        }
        override val delta: BigDecimal get() = amount.amount.negate()
        override val unitId: Uuid get() = amount.unitId
        override val medKitId: Uuid get() = from
    }

    /** Приехала: событие произошло в аптечке назначения, поэтому [medKitId] — это [to]. */
    data class TransferIn(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val from: Uuid,
        val to: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init {
            require(from != to) { "перенос внутри одной аптечки остаток не меняет" }
            requireNote(note)
        }
        override val delta: BigDecimal get() = amount.amount
        override val unitId: Uuid get() = amount.unitId
        override val medKitId: Uuid get() = to
    }

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
    data class RemoteChange(
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
                field = "RemoteChange.delta",
                maxScale = QUANTITY_SCALE,
                maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
            )
            requireNote(note)
        }
    }

    /** Пачка перестала быть видимой: из учёта уходит весь остаток, что мы последним видели. */
    data class AccessLost(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        override val medKitId: Uuid,
        override val observedAt: Instant,
        override val occurredAt: Instant? = null,
        override val operationId: Uuid? = null,
        override val note: String? = null
    ) : StockAdjustment {
        init { requireNote(note) }
        override val delta: BigDecimal get() = amount.amount.negate()
        override val unitId: Uuid get() = amount.unitId
    }
}

private fun requireNote(note: String?) {
    requireOptionalText(note, ADJUSTMENT_NOTE_MAX_LENGTH, "StockAdjustment.note")
}
