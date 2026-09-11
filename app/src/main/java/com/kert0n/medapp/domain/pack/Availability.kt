package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Сколько доступно мне по каждой пачке — вход расчётов препарата курса (PLAN D5). Расклад
 * полный: у каждой пачки, о которой спрашивают, число есть, и спрашивать о пачке, которой в
 * раскладе нет, — ошибка вызывающего, а не состояние запаса.
 */
class Availability(availableToMe: Map<Uuid, Quantity>) {

    /**
     * Своя копия, а не переданная карта: расклад, посчитанный один раз, не должен меняться вслед
     * за тем, кто его собрал. Обёрткой-`value class` тут не обойтись — она хранит ту же ссылку.
     */
    private val availableToMe: Map<Uuid, Quantity> = availableToMe.toMap()

    fun of(packageId: Uuid): Quantity =
        requireNotNull(availableToMe[packageId]) { "расклад не называет пачку $packageId" }

    /** Сколько целых доз даёт пачка. */
    fun dosesOf(packageId: Uuid, dose: Dose): Doses = of(packageId).dosesIn(dose)

    companion object {

        /** Расклад из посчитанной доступности. */
        fun from(packages: List<PackageAvailability>): Availability =
            Availability(packages.associate { it.packageId to it.availableToMe })
    }
}
