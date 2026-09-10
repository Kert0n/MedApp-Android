package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Сколько доступно мне по каждой пачке — вход всех расчётов курса (PLAN D5, H1).
 *
 * **Отсутствие пачки значит «неизвестно», а не ноль.** Пока это был голый `Map`, правило
 * приходилось повторять прозой в четырёх KDoc подряд, и читалось оно всё равно по-разному:
 * обеспечение считало неизвестную пачку нулём, а предел ползунка сохранял прежнее выделение.
 * Оба ответа верны, но выбирать между ними должен вызывающий явно, а не читатель — по памяти.
 * Здесь «неизвестно» это `null` из [known] и [dosesOf], то есть вопрос, на который нельзя не
 * ответить.
 *
 * Числа сюда кладёт [PackageAvailability.availableToMe]: при требуемой сверке его нет, и пачка в
 * раскладе не появляется вовсе — выдуманный предел был бы обещанием лекарства, которого может не
 * быть (PLAN D4).
 */
@JvmInline
value class Availability(private val availableToMe: Map<Uuid, Quantity>) {

    /** `null` — про эту пачку мы не знаем; ноль — знаем, что не осталось ничего. */
    fun known(packageId: Uuid): Quantity? = availableToMe[packageId]

    /**
     * Сколько целых доз даёт пачка. `null` — неизвестно.
     *
     * Целых: доза берётся из одной упаковки и между пачками не делится (PLAN D5).
     */
    fun dosesOf(packageId: Uuid, dose: Quantity): Doses? = known(packageId)?.dosesIn(dose)

    companion object {

        val nothingKnown: Availability = Availability(emptyMap())

        /**
         * Собирает расклад из посчитанной доступности пачек.
         *
         * Пачка без числа (требуется сверка) в расклад не попадает: это и есть единственное
         * место, где «неизвестно» превращается в отсутствие ключа.
         */
        fun from(packages: List<PackageAvailability>): Availability = Availability(
            packages.mapNotNull { pkg -> pkg.availableToMe?.let { pkg.packageId to it } }.toMap()
        )
    }
}
