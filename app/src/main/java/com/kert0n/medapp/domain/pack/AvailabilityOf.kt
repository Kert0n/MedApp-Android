package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity

/**
 * Считает доступность пачки: чужие брони, свободное для любого и доступное мне (PLAN D4).
 *
 * Чистая функция со всеми входами явно — как остальные вычислители (решение PR 3). [pkg]
 * приходит **аргументом**, а не остаётся в результате: сущность внутри значения сравнивалась бы
 * по `id` и прятала изменение количества.
 *
 * [amount] — готовая оценка количества. Свёртка незакрытых команд очереди сюда не заглядывает:
 * какие команды входят в расчёт и в каком порядке — правило синхронизации (PLAN E1), а домену
 * остаются правила о полученном числе.
 *
 * **Броней два вида, свои и чужие.** Третьего не бывает: моя бронь — это то, что я сам и заявил,
 * источник истины здесь устройство, а сервер принимает сказанное. Поэтому из доступного мне
 * вычитается только чужое, а [myAllocation] отделяет «занято моим курсом» от «свободно любому».
 */
fun availabilityOf(
    pkg: Package,
    amount: EffectiveAmount,
    myAllocation: Quantity = Quantity.zero(pkg.quantity.unitId)
): PackageAvailability {
    val unitId = pkg.quantity.unitId
    require(myAllocation.unitId == unitId) { "выделение измеряется единицей пачки" }
    val observed = when (amount) {
        is EffectiveAmount.Known -> amount.quantity
        is EffectiveAmount.NeedsRecount -> amount.lastObserved
    }
    require(observed.unitId == unitId) { "оценка количества измеряется единицей пачки" }

    // Формула чужих броней — одна, и живёт она на самой картине броней: считать её здесь заново
    // значило бы завести второй знак вычитания, который разойдётся с первым.
    val claims = pkg.claims
    val reservedByOthers =
        if (claims == null) Quantity.zero(unitId) else Quantity(claims.reservedByOthers, unitId)

    return PackageAvailability(
        packageId = pkg.id,
        expiresOn = pkg.facts.expiresOn,
        amount = amount,
        reservedByOthers = reservedByOthers,
        myAllocation = myAllocation
    )
}
