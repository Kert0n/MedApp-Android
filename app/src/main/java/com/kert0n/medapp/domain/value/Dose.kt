package com.kert0n.medapp.domain.value

import kotlin.uuid.Uuid

/**
 * Доза — сколько лекарства принимают за раз. Величина, и нулём она не бывает: нулевая доза это не
 * лечение, а деление на ноль в обеспечении. Правило живёт на самой величине, поэтому его
 * соблюдает любой путь — и назначение курса, и приём, и восстановление сохранённого.
 *
 * Отдельно от [Quantity] потому, что обычному остатку ноль необходим: пачка кончается, пересчёт
 * находит ноль, и запрещать его нельзя.
 */
@JvmInline
value class Dose(val quantity: Quantity) {

    init {
        require(!quantity.isZero) { "доза не бывает нулевой" }
    }

    val unitId: Uuid get() = quantity.unitId

    /** Сколько это в единицах пачки: доза, взятая названное число раз. */
    operator fun times(doses: Doses): Quantity = quantity * doses

    override fun toString(): String = "доза $quantity"
}
