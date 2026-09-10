package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.model.value.Doses
import com.kert0n.medapp.domain.model.value.Quantity

/**
 * Сколько целых доз остаётся выделено источнику после подтверждённого приёма:
 *
 * ```
 * floor( max(0, min(allocatedDoses × dose − taken, availableAfter)) / dose )
 * ```
 *
 * Два ограничения одновременно, и оба нужны (PLAN D5). Прежний выделенный объём за вычетом
 * фактического расхода — потому что человек выделил столько и не больше; доступный остаток после
 * расхода — потому что физически в пачке может оказаться меньше, чем он выделял.
 *
 * Отклонение факта от плана здесь и разбирается. Пять доз по две таблетки, принята одна: расход
 * один, остаётся четыре целых выделенных дозы, одна освобождается — половина дозы не держит
 * брони, потому что доза между пачками не делится. Принято три из десяти: остаток семь, и
 * выделено не больше трёх целых доз.
 *
 * **Нулевое выделение расходом не оживает.** Приём из пачки, которую человек курсу не выделял —
 * например, единственной оставшейся, — новой брони не создаёт: заявлять чужой запас за человека
 * приложение не станет.
 */
fun dosesAfterIntake(
    allocatedDoses: Doses,
    dose: Quantity,
    taken: Quantity,
    availableAfter: Quantity
): Doses {
    require(!dose.isZero) { "нулевая доза не делит остаток" }
    // Единица одна на все три величины: сравнивать выделенное с доступным в разных единицах
    // нельзя, а `minusOrZero` проверит только два из трёх аргументов.
    require(availableAfter.unitId == dose.unitId) {
        "доступный остаток измеряется единицей дозы: ${availableAfter.unitId} и ${dose.unitId}"
    }
    if (allocatedDoses.isNone) return Doses.none
    val allocatedAmount = dose * allocatedDoses
    val leftAllocated = allocatedAmount.minusOrZero(taken)
    val limited = if (leftAllocated.amount <= availableAfter.amount) leftAllocated else availableAfter
    return limited.dosesIn(dose)
}
