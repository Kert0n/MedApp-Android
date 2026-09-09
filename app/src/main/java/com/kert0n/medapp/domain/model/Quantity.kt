package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import kotlin.uuid.Uuid

/** Разрядность величин у сервера — `numeric(19, 6)`; шесть знаков это деление таблетки и капли. */
const val QUANTITY_SCALE = 6

const val QUANTITY_MAX_INTEGER_DIGITS = 13

/** 13 разрядов, точка и 6 знаков, плюс запас на ведущий ноль. */
const val QUANTITY_MAX_INPUT_LENGTH = 21

/** Почему введённая строка не стала количеством. Экран показывает причину, а не «неверный ввод». */
enum class QuantityFormatReason(internal val reason: String) {
    EMPTY("количество не введено"),
    TOO_LONG("ввод длиннее $QUANTITY_MAX_INPUT_LENGTH символов"),
    NOT_A_DECIMAL("количество — десятичное число без знака и экспоненты"),
    TOO_MANY_FRACTION_DIGITS("после точки не больше $QUANTITY_SCALE знаков"),
    TOO_MANY_INTEGER_DIGITS("до точки не больше $QUANTITY_MAX_INTEGER_DIGITS разрядов")
}

class QuantityFormatException(val formatReason: QuantityFormatReason) :
    IllegalArgumentException(formatReason.reason)

/** Разбор идёт до `BigDecimal`, чтобы длинный или чужой ввод не дошёл до конструктора вовсе. */
private val DECIMAL_INPUT = Regex("""^\d+(\.\d+)?$""")

/**
 * Количество вместе с единицей: величины в разных единицах не складываются даже случайно.
 *
 * Равенство — по числовому значению, а не по умолчанию data-класса: `BigDecimal.equals`
 * различает `1` и `1.000000` по масштабу, а сервер отвечает всегда шестью знаками (PLAN B2).
 *
 * Ноль допустим: остаток бывает нулевым. Строгая положительность — правило **операции**
 * (приём, бронь, начальный остаток), а не значения, и живёт на сетевой границе.
 */
data class Quantity(val amount: BigDecimal, val unitId: Uuid) {

    init {
        require(amount.signum() >= 0) { "количество не бывает отрицательным" }
        require(amount.scale() <= QUANTITY_SCALE) {
            "после точки не больше $QUANTITY_SCALE знаков"
        }
        require(amount.precision() - amount.scale() <= QUANTITY_MAX_INTEGER_DIGITS) {
            "до точки не больше $QUANTITY_MAX_INTEGER_DIGITS разрядов"
        }
    }

    val isZero: Boolean get() = amount.signum() == 0

    /** Ровно то, что уходит на провод и ложится в базу: без экспоненты, без знака (PLAN B2, F3). */
    fun toWire(): String = amount.toPlainString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Quantity) return false
        return unitId == other.unitId && amount.compareTo(other.amount) == 0
    }

    /**
     * По значению, а не по масштабу: `BigDecimal.hashCode` учитывает `scale`, и `1` с `1.000000`
     * получили бы разные хеши при равных значениях — одна и та же пачка терялась бы в `Map`.
     */
    override fun hashCode(): Int = 31 * unitId.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "${toWire()} @$unitId"

    companion object {

        fun zero(unitId: Uuid): Quantity = Quantity(BigDecimal.ZERO, unitId)

        /**
         * Запятая становится точкой. Отвергает пустое, слишком длинное, знак, экспоненту,
         * больше шести знаков после точки и больше тринадцати до неё.
         *
         * Возвращает `Result`, а не бросает: ввод — обычное состояние формы, а не сбой программы
         * (ТЗ 4.3, PLAN J4 REQ-051).
         */
        fun parse(input: String, unitId: Uuid): Result<Quantity> {
            val text = input.trim().replace(',', '.')
            val reason = reject(text)
            return if (reason != null) {
                Result.failure(QuantityFormatException(reason))
            } else {
                Result.success(Quantity(BigDecimal(text), unitId))
            }
        }

        private fun reject(text: String): QuantityFormatReason? {
            if (text.isEmpty()) return QuantityFormatReason.EMPTY
            if (text.length > QUANTITY_MAX_INPUT_LENGTH) return QuantityFormatReason.TOO_LONG
            if (!DECIMAL_INPUT.matches(text)) return QuantityFormatReason.NOT_A_DECIMAL
            val dot = text.indexOf('.')
            val integerDigits = if (dot < 0) text.length else dot
            val fractionDigits = if (dot < 0) 0 else text.length - dot - 1
            if (fractionDigits > QUANTITY_SCALE) {
                return QuantityFormatReason.TOO_MANY_FRACTION_DIGITS
            }
            if (integerDigits > QUANTITY_MAX_INTEGER_DIGITS) {
                return QuantityFormatReason.TOO_MANY_INTEGER_DIGITS
            }
            return null
        }
    }
}
