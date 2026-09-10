package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    /**
     * Что останется к концу [date] в зоне отчёта, если до этого момента из пачки уйдёт [spent].
     *
     * Сколько уйдёт — считают курсы: сколько приёмов впереди и чем они обеспечены, знают они, а
     * пачке остаётся вычесть названное число. Дата **включительна**: момент прогноза — начало
     * следующих суток в зоне отчёта, и приёмы этого дня уже вычтены.
     *
     * Горизонт — не дальше трёх календарных месяцев (ТЗ 4.1.1.10): дальше расписание и остатки
     * значат слишком мало, чтобы обещать число.
     */
    fun forecastOn(
        date: LocalDate,
        reportZone: ZoneId,
        now: Instant,
        spent: Quantity = Quantity.zero(reservedByOthers.unitId)
    ): PackageForecast {
        // `atZone().toLocalDate()`: `LocalDate.ofInstant` требует API 34 при нижней границе 29.
        val todayThere = now.atZone(reportZone).toLocalDate()
        require(!date.isBefore(todayThere)) { "прогноз считается вперёд, а не назад: $date" }
        require(!date.isAfter(todayThere.plusMonths(PackageForecast.MAX_MONTHS))) {
            "горизонт прогноза — ${PackageForecast.MAX_MONTHS} календарных месяца, запрошено $date"
        }
        return PackageForecast(
            packageId = packageId,
            at = date.plusDays(1).atStartOfDay(reportZone).toInstant(),
            amount = when (val base = amount) {
                EffectiveAmount.Unknown -> base
                is EffectiveAmount.Known -> EffectiveAmount.Known(base.quantity.minusOrZero(spent))
            },
            reservedByOthers = reservedByOthers,
            // Просрочка помечается на дату отчёта: к третьему месяцу годной пачка быть перестанет.
            expired = isExpiredOn(date)
        )
    }

    /** Просрочка только помечает: количество не списывается, пачка остаётся источником. */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isExpiredOn(date) == true

    fun expiresSoonOn(date: LocalDate): Boolean =
        expiresOn?.expiresWithin(date, ExpiryDate.SOON_DAYS) == true
}
