package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import kotlin.uuid.Uuid

/** Разрядность величин у сервера — `numeric(19, 6)`; шесть знаков это деление таблетки и капли. */
const val QUANTITY_SCALE = 6

/**
 * Предел целой части. Взят у серверного `numeric(19, 6)` и **принят как продуктовый**: величины
 * такого размера в учёте лекарств не встречаются, и держать разные пределы для уезжающих и
 * неуезжающих величин значило бы объяснять человеку два разных ограничения (решение PLAN C1).
 */
const val QUANTITY_MAX_INTEGER_DIGITS = 13

/** Потолок числа доз: расписание такого размера отвергается задолго до этого (PLAN H1). */
private val MAX_DOSES = BigDecimal(Int.MAX_VALUE)

/**
 * Количество вместе с единицей: величины в разных единицах не складываются даже случайно.
 *
 * Равенство — по числовому значению, а не по умолчанию data-класса: `BigDecimal.equals`
 * различает `1` и `1.000000` по масштабу, а сервер отвечает всегда шестью знаками (PLAN B2).
 *
 * Ноль допустим: остаток бывает нулевым. Строгая положительность — правило **операции**
 * (приём, бронь, начальный остаток), а не значения, и живёт на сетевой границе.
 *
 * Разбора строк здесь нет: величина требует готовое число, а как его получили из ввода — забота
 * `presentation/mapper`. Формата для провода и базы тоже нет: строку запроса задаёт сетевой
 * маппер, строку колонки — конвертер хранения (PLAN H1).
 */
data class Quantity(val amount: BigDecimal, val unitId: Uuid) {

    init {
        requireNonNegativeDecimal(
            amount = amount,
            field = "количество",
            maxScale = QUANTITY_SCALE,
            maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
        )
    }

    val isZero: Boolean get() = amount.signum() == 0

    operator fun plus(other: Quantity): Quantity {
        requireSameUnit(other)
        return Quantity(amount + other.amount, unitId)
    }

    /**
     * Бросает при нехватке: приём пяти таблеток из остатка в три не должен выглядеть успешным,
     * а списание в минус запрещено (PLAN D1, D5).
     */
    operator fun minus(other: Quantity): Quantity {
        requireSameUnit(other)
        require(amount >= other.amount) { "нехватка: $this меньше $other" }
        return Quantity(amount - other.amount, unitId)
    }

    /** Для показа доступности, где отрицательное просто не показывается (PLAN D4). */
    fun minusOrZero(other: Quantity): Quantity {
        requireSameUnit(other)
        return if (amount >= other.amount) Quantity(amount - other.amount, unitId) else zero(unitId)
    }

    /**
     * Умножение только на целое число доз: количество умножается на счётчик приёмов, а не на
     * другую величину — произведение таблеток на таблетки смысла не имеет.
     */
    operator fun times(count: Int): Quantity {
        require(count >= 0) { "число доз не бывает отрицательным" }
        return Quantity(amount * count.toBigDecimal(), unitId)
    }

    fun covers(dose: Quantity): Boolean {
        requireSameUnit(dose)
        return amount >= dose.amount
    }

    /**
     * Сколько целых доз помещается. Именно целых: доза берётся из одной упаковки и между пачками
     * не делится, поэтому по одной таблетке в двух пачках при дозе в две таблетки дают ноль доз,
     * а не одну (PLAN D5). Нулевая доза — ошибка, делить на неё нечего.
     */
    fun dosesIn(dose: Quantity): Int {
        requireSameUnit(dose)
        require(!dose.isZero) { "нулевая доза не делит остаток" }
        val whole = amount.divideToIntegralValue(dose.amount)
        return if (whole > MAX_DOSES) Int.MAX_VALUE else whole.toInt()
    }

    private fun requireSameUnit(other: Quantity) {
        require(unitId == other.unitId) {
            "величины в разных единицах не считаются вместе: $unitId и ${other.unitId}"
        }
    }

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

    override fun toString(): String = "${amount.toPlainString()} @$unitId"

    companion object {

        fun zero(unitId: Uuid): Quantity = Quantity(BigDecimal.ZERO, unitId)
    }
}
