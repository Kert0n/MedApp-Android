package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Сколько доступно по одной пачке (PLAN D4): сколько есть, сколько заявлено чужими, сколько могу
 * взять я и сколько свободно любому. Величина: все поля — числа, и расход 20 → 19 даёт другое
 * значение. Производные — геттеры, поэтому «свободно 5» при нулевом остатке не записать. Строится
 * из пачки: чужие брони берутся у её картины броней, моё выделение приносят курсы.
 */
data class PackageAvailability(
    val packageId: Uuid,
    val expiresOn: ExpiryDate?,
    val amount: EffectiveAmount,
    val reservedByOthers: Quantity,
    val myAllocation: Quantity
) {

    constructor(
        pkg: Package,
        amount: EffectiveAmount,
        myAllocation: Quantity = Quantity.zero(pkg.quantity.unitId)
    ) : this(
        packageId = pkg.id,
        expiresOn = pkg.facts.expiresOn,
        amount = amount,
        reservedByOthers = pkg.claims?.let { Quantity(it.reservedByOthers, pkg.quantity.unitId) }
            ?: Quantity.zero(pkg.quantity.unitId),
        myAllocation = myAllocation
    )

    init {
        val unitId = reservedByOthers.unitId
        require(myAllocation.unitId == unitId) { "выделение измеряется единицей пачки" }
        require(amount !is EffectiveAmount.Known || amount.quantity.unitId == unitId) {
            "оценка количества измеряется единицей пачки"
        }
    }

    /** `null` — количество неизвестно до сверки. */
    val effective: Quantity? get() = amount.quantityOrNull

    /** Сколько могу взять я: вычитается только чужое, свою бронь я заявил сам. */
    val availableToMe: Quantity? get() = effective?.minusOrZero(reservedByOthers)

    /**
     * Свободно любому: доступное мне без моего выделения. Считается не от суммы броней: моя
     * серверная бронь отстаёт от локального выделения на то, что ещё не уехало (D4).
     */
    val freeForAnyone: Quantity? get() = availableToMe?.minusOrZero(myAllocation)

    val requiresRecount: Boolean get() = amount == EffectiveAmount.Unknown

    /** Просрочка только помечает: количество не списывается, пачка остаётся источником. */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isExpiredOn(date) == true

    fun expiresSoonOn(date: LocalDate): Boolean =
        expiresOn?.expiresWithin(date, ExpiryDate.SOON_DAYS) == true
}
