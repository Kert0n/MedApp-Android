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
 *
 * [effective] — число, которое считает очередь: подтверждённый остаток с незакрытыми командами
 * поверх (E1). Число есть всегда: истина по количеству — сервер, а до ответа устройство знает
 * то, что само отправило.
 */
data class PackageAvailability(
    val packageId: Uuid,
    val expiresOn: ExpiryDate?,
    val effective: Quantity,
    val reservedByOthers: Quantity,
    val myAllocation: Quantity
) {

    constructor(
        pkg: Package,
        effective: Quantity,
        myAllocation: Quantity = Quantity.zero(pkg.quantity.unit)
    ) : this(
        packageId = pkg.id,
        expiresOn = pkg.facts.expiresOn,
        effective = effective,
        reservedByOthers = pkg.claims?.let { Quantity(it.reservedByOthers, pkg.quantity.unit) }
            ?: Quantity.zero(pkg.quantity.unit),
        myAllocation = myAllocation
    )

    init {
        val unit = effective.unit
        require(reservedByOthers.unit == unit) { "чужие брони измеряются единицей пачки" }
        require(myAllocation.unit == unit) { "выделение измеряется единицей пачки" }
    }

    /** Сколько могу взять я: вычитается только чужое, свою бронь я заявил сам. */
    val availableToMe: Quantity get() = effective.minusOrZero(reservedByOthers)

    /**
     * Свободно любому: доступное мне без моего выделения. Считается не от суммы броней: моя
     * серверная бронь отстаёт от локального выделения на то, что ещё не уехало (D4).
     */
    val freeForAnyone: Quantity get() = availableToMe.minusOrZero(myAllocation)

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
        spent: Quantity = Quantity.zero(effective.unit)
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
            remaining = effective.minusOrZero(spent),
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
