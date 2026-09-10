package com.kert0n.medapp.domain.value

/**
 * Число целых доз — единица, которой продукт меряет выделение, потребность и обеспечение.
 *
 * **Почему целые.** Доза берётся из одной упаковки и между пачками не делится: по одной таблетке
 * в двух пачках при дозе в две таблетки дают ноль доз, а не одну. В таблетках сумма 1 + 1
 * сравнялась бы с потребностью, ползунки зажались бы, а покрытие осталось нулевым — состояние без
 * выхода (PLAN D5, C1 «Выделение»).
 *
 * **Почему величина, а не `Int`.** Неотрицательность — правило самой единицы, и пока она жила
 * `Int`-ом, одно и то же `require(... >= 0)` было переписано девять раз в восьми файлах; десятое
 * место забыли бы. Здесь неверное состояние невыразимо, а не отвергается в каждом вызывающем.
 *
 * Единицы у доз нет намеренно: доза курса — [Quantity], а это счётчик приёмов, и умножение
 * счётчика на дозу даёт количество ([Quantity.times]).
 */
data class Doses(val count: Int) : Comparable<Doses> {

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

        /** Один приём стоит ровно одной дозы: с этим шагом расходуется стек источников (D5). */
        val one: Doses = Doses(1)
    }
}
