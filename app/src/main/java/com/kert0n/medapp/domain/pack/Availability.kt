package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Сколько доступно мне по каждой пачке — вход расчётов препарата курса (PLAN D5). Нет пачки —
 * значит неизвестно, а не ноль: [known] и [dosesOf] отвечают `null`, и что с этим делать, решает
 * вызывающий. Пачка, требующая сверки, в расклад не попадает (D4).
 */
class Availability(availableToMe: Map<Uuid, Quantity>) {

    /**
     * Своя копия, а не переданная карта: расклад, посчитанный один раз, не должен меняться вслед
     * за тем, кто его собрал. Обёрткой-`value class` тут не обойтись — она хранит ту же ссылку.
     */
    private val availableToMe: Map<Uuid, Quantity> = availableToMe.toMap()

    /** `null` — про эту пачку мы не знаем; ноль — знаем, что не осталось ничего. */
    fun known(packageId: Uuid): Quantity? = availableToMe[packageId]

    /** Сколько целых доз даёт пачка; `null` — неизвестно. */
    fun dosesOf(packageId: Uuid, dose: Quantity): Doses? = known(packageId)?.dosesIn(dose)

    companion object {

        val nothingKnown: Availability = Availability(emptyMap())

        /** Расклад из посчитанной доступности; пачка без числа в него не попадает. */
        fun from(packages: List<PackageAvailability>): Availability = Availability(
            packages.mapNotNull { pkg -> pkg.availableToMe?.let { pkg.packageId to it } }.toMap()
        )
    }
}
