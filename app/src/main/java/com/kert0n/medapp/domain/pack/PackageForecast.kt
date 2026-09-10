package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.course.CourseStatus
import com.kert0n.medapp.domain.course.PlannedCourse
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Прогноз по одной пачке на момент [at]: количество к этому моменту или «неизвестно», если оно
 * неизвестно и сейчас. Просрочка помечается [expired], а не обнуляет остаток (PLAN D3); чужие
 * брони показываются отдельно — таблетки в пачке лежат, просто заявлены другими.
 */
data class PackageForecast(
    val packageId: Uuid,
    val at: Instant,
    val amount: EffectiveAmount,
    val reservedByOthers: Quantity,
    val expired: Boolean
) {

    val remaining: Quantity? get() = amount.quantityOrNull

    val requiresRecount: Boolean get() = amount == EffectiveAmount.Unknown

    companion object {
        /** Горизонт прогноза: не дальше трёх календарных месяцев (ТЗ 4.1.1.10). */
        const val MAX_MONTHS = 3L
    }
}

/**
 * Прогноз по каждой пачке на конец [date] в зоне [reportZone]; момент прогноза — начало
 * следующих суток. Будущие приёмы берутся из календаря курсов, а не из материализованного окна
 * (PLAN F4), и списываются только обеспеченные — так, как их расходует препарат курса (D5).
 * [resolved] — уже отвеченные пункты: их расход уже в остатке или не состоялся.
 */
fun remainingOn(
    date: LocalDate,
    reportZone: ZoneId,
    now: Instant,
    packages: List<PackageAvailability>,
    courses: List<PlannedCourse>,
    resolved: List<CourseIntake>
): List<PackageForecast> {
    // `atZone().toLocalDate()`: `LocalDate.ofInstant` требует API 34 при нижней границе 29.
    val todayThere = now.atZone(reportZone).toLocalDate()
    require(!date.isBefore(todayThere)) { "прогноз считается вперёд, а не назад: $date" }
    require(!date.isAfter(todayThere.plusMonths(PackageForecast.MAX_MONTHS))) {
        "горизонт прогноза — ${PackageForecast.MAX_MONTHS} календарных месяца, запрошено $date"
    }
    val until = date.plusDays(1).atStartOfDay(reportZone).toInstant()
    val availability = Availability.from(packages)

    // Пункт опознаётся курсом и назначенными датой со временем, как при материализации (F4).
    val answered = resolved
        .filter { it.status != IntakeStatus.PLANNED }
        .map { Triple(it.courseId, it.slot.localDate, it.slot.localTime) }
        .toSet()

    val spentDoses = HashMap<Uuid, Doses>()
    val doseOf = HashMap<Uuid, Quantity>()
    for (course in courses) {
        if (course.status != CourseStatus.ACTIVE) continue
        val dose = course.dose
        val ahead = Doses(
            course.schedule.occurrences(now, until)
                .count { Triple(course.id, it.localDate, it.localTime) !in answered }
        )
        for ((packageId, doses) in course.medicine.spend(dose, ahead, availability)) {
            spentDoses[packageId] = (spentDoses[packageId] ?: Doses.none) + doses
            doseOf[packageId] = dose
        }
    }

    return packages.map { stock ->
        val doses = spentDoses[stock.packageId] ?: Doses.none
        val dose = doseOf[stock.packageId]
        val amount = when (val base = stock.amount) {
            EffectiveAmount.Unknown -> base
            is EffectiveAmount.Known -> EffectiveAmount.Known(
                if (dose == null) base.quantity else base.quantity.minusOrZero(dose * doses)
            )
        }
        PackageForecast(
            packageId = stock.packageId,
            at = until,
            amount = amount,
            reservedByOthers = stock.reservedByOthers,
            expired = stock.isExpiredOn(date)
        )
    }
}
