package com.kert0n.medapp.domain.calc.coverage

import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Сколько целых доз реально даёт каждый источник, в порядке стека: минимум из выделения человека
 * и того, что физически есть в пачке.
 *
 * Одно место на две задачи — раскладку будущих пунктов по пачкам и прогноз остатка. Пока их было
 * две, «остаток меньше дозы не переливается» пришлось бы соблюсти дважды и однажды забыть.
 *
 * Отсутствие пачки в [availability] значит «неизвестно», и вместимость такого источника — ноль:
 * до сверки он за обеспеченный не выдаётся (PLAN D5).
 */
internal fun sourceCapacity(
    course: Course,
    dose: Quantity,
    availability: Map<Uuid, Quantity>
): List<Pair<Uuid, Int>> = course.sources.map { source ->
    val wholeDoses = availability[source.packageId]?.dosesIn(dose) ?: 0
    source.packageId to minOf(source.allocatedDoses, wholeDoses)
}

/**
 * Расход [doses] доз по стеку **сверху вниз**: сколько уйдёт из каждой пачки.
 *
 * Пачки, из которых не уходит ничего, в ответе не появляются: «ноль доз из этой пачки» и
 * «эта пачка не участвует» — одно и то же утверждение, и второе короче.
 */
internal fun spendTopDown(capacity: List<Pair<Uuid, Int>>, doses: Int): Map<Uuid, Int> {
    require(doses >= 0) { "число приёмов не бывает отрицательным: $doses" }
    var left = doses
    val spent = LinkedHashMap<Uuid, Int>()
    for ((packageId, fits) in capacity) {
        if (left == 0) break
        val taken = minOf(fits, left)
        if (taken == 0) continue
        spent[packageId] = (spent[packageId] ?: 0) + taken
        left -= taken
    }
    return spent
}
