package com.kert0n.medapp.domain.medkit

/**
 * Что лежит в аптечке, глазами списка: сколько живых упаковок и сколько из них просрочено
 * (PLAN H3 №2). Величина: две аптечки с одинаковым содержимым для списка одинаковы.
 *
 * Просрочка считается отдельно, а не выводится из числа пачек: человек решает по ней, куда идти
 * первым делом, и она видна раньше, чем он откроет аптечку.
 */
data class MedKitContents(val packages: Int = 0, val expired: Int = 0) {

    init {
        require(packages >= 0) { "пачек не бывает отрицательное число" }
        require(expired in 0..packages) { "просроченных не больше, чем всех" }
    }

    val hasExpired: Boolean get() = expired > 0

    val isEmpty: Boolean get() = packages == 0
}
