package com.kert0n.medapp.domain.value

/**
 * Число целых доз — единица, которой меряют выделение, потребность и обеспечение. Целых, потому
 * что доза берётся из одной пачки и между пачками не делится: по таблетке в двух пачках при дозе в
 * две — ноль доз, а не одна (PLAN D5). Отрицательным не бывает.
 */
@JvmInline
value class Doses(val count: Int) : Comparable<Doses> {

    init {
        require(count >= 0) { "число доз не бывает отрицательным: $count" }
    }

    val isNone: Boolean get() = count == 0

    operator fun plus(other: Doses): Doses = Doses(count + other.count)

    /**
     * Бросает при нехватке — как [Quantity.minus]: «обеспечено больше, чем нужно» это ошибка
     * расчёта, и показывать её нулём значило бы её спрятать.
     */
    operator fun minus(other: Doses): Doses {
        require(count >= other.count) { "нехватка доз: $count меньше ${other.count}" }
        return Doses(count - other.count)
    }

    /** Для пределов и разностей, где отрицательное просто не показывается (PLAN D5). */
    fun minusOrNone(other: Doses): Doses =
        if (count >= other.count) Doses(count - other.count) else none

    override fun compareTo(other: Doses): Int = count.compareTo(other.count)

    companion object {
        val none: Doses = Doses(0)

        /** Один приём — ровно одна доза. */
        val one: Doses = Doses(1)
    }
}
