package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.course.PlannedCourse
import com.kert0n.medapp.domain.course.CourseStatus
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Сколько останется в каждой упаковке к концу [date] в зоне отчёта.
 *
 * **Считается формулой, а не строками.** Материализовано только окно в шестьдесят дней (PLAN F4),
 * поэтому прогноз на три месяца по строкам приёмов был бы прогнозом на два: календарь считает
 * вычислитель расписания, а не запрос к базе.
 *
 * Дата **включительна** в [reportZone]: приёмы этого дня уже вычтены, и момент прогноза —
 * начало следующих суток. Именно этой границей включительность и выражена, а не «плюс сутки
 * где-то в запросе».
 *
 * Списываются только **обеспеченные** будущие приёмы: расход идёт сверху вниз по стеку в пределах
 * выделения и физического остатка (PLAN D5, H1). Пункт, до которого источники не дотягиваются,
 * ничего не вычитает — он и в жизни не состоится сам собой.
 *
 * [resolved] — уже отвеченные приёмы: их расход либо уже в остатке, либо его не было вовсе.
 * Считать их снова значило бы вычесть подтверждённую вчера таблетку второй раз.
 *
 * Часы приходят аргументом ([now]), а не берутся из системы: иначе прогноз непроверяем тестом.
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

    // Доступность — то же самое, чем считаются обеспечение и пределы ползунков: пачка без
    // известного остатка в расчёт не входит, и её прогноз останется неизвестным.
    val availability = Availability.from(packages)

    // Пункт опознаётся курсом и назначенными датой со временем — тем же, чем он опознаётся при
    // повторной материализации расписания (PLAN F4).
    val answered = resolved
        .filter { it.status != IntakeStatus.PLANNED }
        .map { Triple(it.courseId, it.slot.localDate, it.slot.localTime) }
        .toSet()

    val spentDoses = HashMap<Uuid, Doses>()
    val doseOf = HashMap<Uuid, Quantity>()
    for (course in courses) {
        if (course.status != CourseStatus.ACTIVE) continue
        val schedule = course.schedule
        val dose = course.dose
        val ahead = Doses(
            schedule.occurrences(now, until)
                .count { Triple(course.id, it.localDate, it.localTime) !in answered }
        )
        val spent = course.medicine.spend(dose, ahead, availability)
        for ((packageId, doses) in spent) {
            spentDoses[packageId] = (spentDoses[packageId] ?: Doses.none) + doses
            doseOf[packageId] = dose
        }
    }

    return packages.map { stock ->
        // База прогноза — физический остаток, а не «сколько моего»: чужие брони показываются
        // отдельным числом. Смешав их, мы обещали бы, что таблеток в пачке нет, хотя они лежат.
        val doses = spentDoses[stock.packageId] ?: Doses.none
        val dose = doseOf[stock.packageId]
        val amount = when (val base = stock.amount) {
            // Требование сверки переносится как есть, вместе с операциями: считать будущее от
            // числа, которого мы не знаем, значило бы выдумать остаток (PLAN E3).
            is EffectiveAmount.NeedsRecount -> base
            // Прогноз не подтверждённее своей базы: незакрытые локальные изменения остаются в нём.
            is EffectiveAmount.Known -> EffectiveAmount.Known(
                quantity = if (dose == null) base.quantity
                else base.quantity.minusOrZero(dose * doses),
                confirmed = base.confirmed
            )
        }
        PackageForecast(
            packageId = stock.packageId,
            at = until,
            amount = amount,
            reservedByOthers = stock.reservedByOthers,
            // Просрочка помечается на дату отчёта: к третьему месяцу годной пачка быть перестанет.
            expired = stock.isExpiredOn(date)
        )
    }
}
