package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Верхняя граница ползунка источника, **в целых дозах**:
 *
 * ```
 * maxDoses(i) = min( availableToMe(i).dosesIn(dose), requiredDoses − Σ allocatedDoses(j), j ≠ i )
 * ```
 *
 * Первое слагаемое — сколько целых доз физически даёт пачка; второе — сколько ещё нужно курсу
 * помимо остальных источников. Сумма выделений не превышает потребности, и перераспределять
 * задним числом нечего: хочешь больше во втором источнике — сначала уменьши первый.
 * **Автоматики, снимающей выделение с других источников, нет** — она молча переписывала бы уже
 * принятое человеком решение (PLAN D5, C1).
 *
 * Считается в **целых** дозах, и это то самое, что снимает тупик: доза 2, в двух пачках по одной
 * таблетке — `floor(1/2) = 0` в каждой, выделить нечего и ползунки честно стоят на нуле. В
 * таблетках сумма 1 + 1 сравнялась бы с потребностью на приём, ползунки зажались бы, а покрытие
 * осталось нулевым — состояние без выхода.
 *
 * [availability] — `availableToMe` по пачкам (PLAN D4). **Отсутствие ключа значит «неизвестно»**,
 * а не ноль: при требуемой сверке числовой предел недоступен, и тогда сохраняется последнее
 * выделение — снижать его догадкой нельзя, повышать нечем (PLAN D5).
 *
 * [packageId] не обязан быть источником курса: тот же расчёт отвечает на «сколько можно выделить,
 * если подключить эту пачку».
 */
fun maxDoses(
    packageId: Uuid,
    course: Course,
    requiredDoses: Int,
    availability: Map<Uuid, Quantity>
): Int {
    require(requiredDoses >= 0) { "потребность не бывает отрицательной: $requiredDoses" }
    val dose = requireNotNull(course.dose) { "предел выделения без дозы курса не определён" }
    val allocatedHere = course.sources.firstOrNull { it.packageId == packageId }?.allocatedDoses ?: 0
    val available = availability[packageId] ?: return allocatedHere
    val allocatedElsewhere = course.allocatedDosesTotal - allocatedHere
    val stillNeeded = requiredDoses - allocatedElsewhere
    return maxOf(0, minOf(available.dosesIn(dose), stillNeeded))
}
