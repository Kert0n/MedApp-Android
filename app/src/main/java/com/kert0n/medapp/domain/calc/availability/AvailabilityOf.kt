package com.kert0n.medapp.domain.calc.availability

import com.kert0n.medapp.domain.model.pack.ClaimOwnership
import com.kert0n.medapp.domain.model.pack.EffectiveAmount
import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.value.Quantity
import java.math.BigDecimal

/**
 * Считает доступность пачки: чужие брони, бесхозную бронь, свободное для любого и доступное мне
 * (PLAN D4).
 *
 * Чистая функция со всеми входами явно — как остальные вычислители (решение PR 3). [pkg]
 * приходит **аргументом**, а не остаётся в результате: сущность внутри значения сравнивалась бы
 * по `id` и прятала изменение количества.
 *
 * [amount] — готовая оценка количества. Свёртка незакрытых команд очереди сюда не заглядывает:
 * какие команды входят в расчёт и в каком порядке — правило синхронизации (PLAN E1), а домену
 * остаются правила о полученном числе.
 *
 * [claimOwnership] — смысл моей брони, а не признак ожидающего запроса: от него зависит, считать
 * ли её бесхозной.
 */
fun availabilityOf(
    pkg: Package,
    amount: EffectiveAmount,
    myAllocation: Quantity = Quantity.zero(pkg.quantity.unitId),
    claimOwnership: ClaimOwnership = ClaimOwnership.NoKnownOwner
): PackageAvailability {
    val unitId = pkg.quantity.unitId
    require(myAllocation.unitId == unitId) { "выделение измеряется единицей пачки" }
    val observed = when (amount) {
        is EffectiveAmount.Known -> amount.quantity
        is EffectiveAmount.NeedsRecount -> amount.lastObserved
    }
    require(observed.unitId == unitId) { "оценка количества измеряется единицей пачки" }

    val claims = pkg.claims
    // Сумма минус моя часть, а не сумма целиком: моя часть — снимок сервера, и вычитать её
    // наравне с чужой значило бы отнять у себя собственные таблетки дважды (PLAN D4).
    val reservedByOthers = if (claims == null) Quantity.zero(unitId) else {
        val mine = claims.mine ?: BigDecimal.ZERO
        Quantity(maxOf(claims.total - mine, BigDecimal.ZERO), unitId)
    }
    val orphanClaim = when (claimOwnership) {
        is ClaimOwnership.NoKnownOwner ->
            claims?.mine?.let { Quantity(it, unitId) } ?: Quantity.zero(unitId)
        // Объяснённая назначением и снятая локально брони бесхозными не бывают: назначение
        // разобрано человеком, и вычитать их из доступного нечего.
        is ClaimOwnership.AssignedTo, is ClaimOwnership.ReleasedLocally -> Quantity.zero(unitId)
    }

    return PackageAvailability(
        packageId = pkg.id,
        expiresOn = pkg.facts.expiresOn,
        amount = amount,
        reservedByOthers = reservedByOthers,
        orphanClaim = orphanClaim,
        myAllocation = myAllocation,
        claimOwnership = claimOwnership
    )
}
