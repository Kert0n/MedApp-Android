package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.requireDecimalWithinLimits
import com.kert0n.medapp.domain.value.requireOptionalText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Движение остатка — учётная запись о том, когда, зачем и сколько изменилось **в пачке** помимо
 * приёма (ТЗ 4.1.1.10.2, PLAN D7). Её пишут переходы упаковки и приём снимков, читают отчёты; это
 * не событие и не часть пачки.
 *
 * Запись о пачке, а не о месте. Продукт спрашивает «сколько истрачено» — добавленное минус
 * оставшееся, — и на этот вопрос аптечка не влияет: где коробка лежит сейчас, знает она сама.
 * Перенос между аптечками ничего не тратит и движением не является вовсе.
 *
 * «Зачем» — вид записи, причина утилизации и [note]; «сколько» — величина в единице на момент
 * записи, а знак задаёт вид, а не тот, кто пишет; «когда» — два момента: [occurredAt] случилось
 * (неизвестно только у чужого) и [observedAt] мы узнали. Пачка — ссылкой. Связи с операцией
 * очереди здесь нет: это обвязка данных.
 */
sealed interface StockMovement {

    val id: Uuid
    val pkg: PackageRef
    val unit: QuantityUnit
    val occurredAt: Instant?
    val observedAt: Instant
    val note: String?

    /** Пачка заведена: весь начальный остаток — приход. */
    data class Receipt(
        override val id: Uuid,
        override val pkg: PackageRef,
        val amount: Quantity,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unit: QuantityUnit get() = amount.unit
    }

    /** Пересчитали и увидели [after] вместо [before]: пересчёт находит и больше, и меньше. */
    data class Recount(
        override val id: Uuid,
        override val pkg: PackageRef,
        val before: Quantity,
        val after: Quantity,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init {
            require(before.unit == after.unit) { "пересчёт не меняет единицу" }
            requireNote(note)
        }
        override val unit: QuantityUnit get() = after.unit
    }

    /** Выбросили названное количество по названной причине. */
    data class Disposal(
        override val id: Uuid,
        override val pkg: PackageRef,
        val amount: Quantity,
        val reason: Reason,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unit: QuantityUnit get() = amount.unit

        /** Просрочка и порча названы в D7; прочее человек поясняет заметкой. */
        enum class Reason { EXPIRED, DAMAGED, OTHER }
    }

    /**
     * Остаток изменился на сервере, и это не мы. [delta] — часть расхождения, которую не
     * объясняют наши установленные изменения (формула D7), иначе наш расход попал бы в отчёт
     * дважды. Причины сервер не знает, и она не выдумывается.
     */
    data class RemoteChange(
        override val id: Uuid,
        override val pkg: PackageRef,
        val delta: BigDecimal,
        override val unit: QuantityUnit,
        override val observedAt: Instant,
        override val occurredAt: Instant? = null,
        override val note: String? = null
    ) : StockMovement {
        init {
            requireDecimalWithinLimits(
                amount = delta,
                field = "StockMovement.RemoteChange.delta",
                maxScale = Quantity.SCALE,
                maxIntegerDigits = Quantity.MAX_INTEGER_DIGITS
            )
            requireNote(note)
        }
    }

    /** Пачка перестала быть видимой: из учёта уходит последний виденный остаток. */
    data class AccessLoss(
        override val id: Uuid,
        override val pkg: PackageRef,
        val amount: Quantity,
        override val observedAt: Instant,
        override val occurredAt: Instant? = null,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unit: QuantityUnit get() = amount.unit
    }

    companion object {
        const val NOTE_MAX_LENGTH = 200

        internal fun requireNote(note: String?) {
            requireOptionalText(note, NOTE_MAX_LENGTH, "StockMovement.note")
        }
    }
}
