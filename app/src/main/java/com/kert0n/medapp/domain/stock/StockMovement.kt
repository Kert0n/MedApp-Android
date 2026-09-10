package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.requireDecimalWithinLimits
import com.kert0n.medapp.domain.value.requireOptionalText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Движение остатка — учётная запись о том, когда, зачем и сколько изменилось в пачке помимо
 * приёма (ТЗ 4.1.1.10.2, PLAN D7). Её пишут переходы упаковки и приём снимков, читают отчёты;
 * это не событие и не часть пачки. «Зачем» — вид записи, причина утилизации и [note]; «сколько» —
 * величина в единице на момент записи, а знак задаёт вид, а не тот, кто пишет; «когда» — два
 * момента: [occurredAt] случилось (неизвестно только у чужого) и [observedAt] мы узнали. Связи с
 * операцией очереди здесь нет: это обвязка данных.
 */
sealed interface StockMovement {

    val id: Uuid
    val packageId: Uuid
    val unitId: Uuid
    val occurredAt: Instant?
    val observedAt: Instant
    val note: String?

    /** Пачка заведена: весь начальный остаток — приход. */
    data class Receipt(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unitId: Uuid get() = amount.unitId
    }

    /** Пересчитали и увидели [after] вместо [before]: пересчёт находит и больше, и меньше. */
    data class Recount(
        override val id: Uuid,
        override val packageId: Uuid,
        val before: Quantity,
        val after: Quantity,
        val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init {
            require(before.unitId == after.unitId) { "пересчёт не меняет единицу" }
            requireNote(note)
        }
        override val unitId: Uuid get() = after.unitId
    }

    /** Выбросили названное количество по названной причине. */
    data class Disposal(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val reason: Reason,
        val medKitId: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unitId: Uuid get() = amount.unitId

        /** Просрочка и порча названы в D7; прочее человек поясняет заметкой. */
        enum class Reason { EXPIRED, DAMAGED, OTHER }
    }

    /**
     * Пачку перенесли из [from] в [to]. Одна запись с двумя концами: в источнике остаток
     * уменьшился, в назначении вырос на то же количество, и разойтись концы не могут.
     */
    data class Transfer(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val from: Uuid,
        val to: Uuid,
        override val occurredAt: Instant,
        override val observedAt: Instant,
        override val note: String? = null
    ) : StockMovement {
        init {
            require(from != to) { "перенос внутри одной аптечки остаток не меняет" }
            requireNote(note)
        }
        override val unitId: Uuid get() = amount.unitId
    }

    /**
     * Остаток изменился на сервере, и это не мы. [delta] — часть расхождения, которую не
     * объясняют наши установленные изменения (формула D7), иначе наш расход попал бы в отчёт
     * дважды. Причины сервер не знает, и она не выдумывается.
     */
    data class RemoteChange(
        override val id: Uuid,
        override val packageId: Uuid,
        val delta: BigDecimal,
        override val unitId: Uuid,
        val medKitId: Uuid,
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

    /** Пачка перестала быть видимой: из учёта аптечки уходит последний виденный остаток. */
    data class AccessLoss(
        override val id: Uuid,
        override val packageId: Uuid,
        val amount: Quantity,
        val medKitId: Uuid,
        override val observedAt: Instant,
        override val occurredAt: Instant? = null,
        override val note: String? = null
    ) : StockMovement {
        init { requireNote(note) }
        override val unitId: Uuid get() = amount.unitId
    }

    /**
     * Насколько эта запись изменила остаток в аптечке [medKitId]. Знак задаёт вид: приход
     * положителен, утилизация и утрата доступа отрицательны, пересчёт и чужое изменение — в обе
     * стороны. Концы переноса дают −и+, поэтому перенос внутри выбранных аптечек в их сумме
     * расходом не выглядит (H6).
     */
    fun deltaIn(medKitId: Uuid): BigDecimal {
        val (kit, delta) = when (this) {
            is Receipt -> this.medKitId to amount.amount
            is Recount -> this.medKitId to after.amount - before.amount
            is Disposal -> this.medKitId to amount.amount.negate()
            is RemoteChange -> this.medKitId to delta
            is AccessLoss -> this.medKitId to amount.amount.negate()
            is Transfer -> return when (medKitId) {
                from -> amount.amount.negate()
                to -> amount.amount
                else -> BigDecimal.ZERO
            }
        }
        return if (kit == medKitId) delta else BigDecimal.ZERO
    }

    companion object {
        const val NOTE_MAX_LENGTH = 200

        internal fun requireNote(note: String?) {
            requireOptionalText(note, NOTE_MAX_LENGTH, "StockMovement.note")
        }
    }
}
