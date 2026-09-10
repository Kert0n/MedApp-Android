package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
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
 * Про пачку с требуемой сверкой [Availability] числа не даёт, и тогда сохраняется последнее
 * выделение: снижать его догадкой нельзя, повышать нечем (PLAN D5). Ответ «неизвестно» и ответ
 * «ноль» здесь ведут себя по-разному, поэтому их и различает тип, а не соглашение.
 *
 * [packageId] не обязан быть источником курса: тот же расчёт отвечает на «сколько можно выделить,
 * если подключить эту пачку».
 */
fun maxDoses(
    packageId: Uuid,
    course: PlannedCourse,
    requiredDoses: Doses,
    availability: Availability
): Doses {
    val dose = course.dose
    val allocatedHere =
        course.sources.firstOrNull { it.packageId == packageId }?.allocatedDoses ?: Doses.none
    val available = availability.known(packageId) ?: return allocatedHere
    val allocatedElsewhere = course.allocatedDosesTotal - allocatedHere
    val stillNeeded = requiredDoses.minusOrNone(allocatedElsewhere)
    return minOf(available.dosesIn(dose), stillNeeded)
}
