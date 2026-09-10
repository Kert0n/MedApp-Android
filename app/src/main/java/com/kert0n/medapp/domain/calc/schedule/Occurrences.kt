package com.kert0n.medapp.domain.calc.schedule

import com.kert0n.medapp.domain.model.course.CourseSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Пункты расписания, попадающие в интервал `[from, until)`.
 *
 * **Чистая функция, а не интерфейс.** Вычислитель детерминирован и все входы получает явно:
 * подменять его в тестах нечем и незачем, а интерфейс с единственной реализацией — та же
 * церемония, что фабрика поверх конструктора (решение PR 3, PLAN H1).
 *
 * Начало интервала включительно, конец — нет: соседние окна материализации стыкуются без
 * повтора и без дыры. Дата конца самого расписания при этом **включительная** (PLAN D5), и
 * пункты последнего дня в интервал входят.
 *
 * Окна в шестьдесят дней здесь нет: сколько строить — свойство вызывающего (PLAN F4), а функция
 * считает любой интервал. Поэтому один и тот же расчёт отвечает и материализации окна, и
 * прогнозу на три месяца, и полной оставшейся потребности за пределами кэша.
 */
fun occurrences(
    schedule: CourseSchedule,
    from: Instant,
    until: Instant
): List<ScheduledOccurrence> {
    require(!until.isBefore(from)) { "интервал [from, until) не бывает обратным" }
    if (until == from) return emptyList()
    val zone = schedule.zone
    // Крайние сутки берём с запасом в день с каждой стороны: момент пункта зависит от зоны и от
    // перехода, поэтому по дате границу провести нельзя — отбор идёт уже по моменту.
    // `atZone().toLocalDate()`, а не `LocalDate.ofInstant`: последнее появилось в API 34, а
    // нижняя граница у нас 29 (PLAN H2). Результат тот же.
    val firstDate = maxOf(schedule.start, from.atZone(zone).toLocalDate().minusDays(1))
    val lastDate = minOf(schedule.endInclusive, until.atZone(zone).toLocalDate())
    if (lastDate.isBefore(firstDate)) return emptyList()

    val found = ArrayList<ScheduledOccurrence>()
    var date = firstDate
    while (!date.isAfter(lastDate)) {
        if (date.dayOfWeek in schedule.daysOfWeek) {
            for (time in schedule.times) {
                val at = resolveDaylightSaving(date, time, zone)
                if (!at.isBefore(from) && at.isBefore(until)) {
                    found += ScheduledOccurrence(date, time, at)
                }
            }
        }
        date = date.plusDays(1)
    }
    return found.sortedWith(
        compareBy<ScheduledOccurrence> { it.at }.thenBy { it.localDate }.thenBy { it.localTime }
    )
}

/**
 * Правило перехода на летнее и зимнее время. Одно на весь проект, и записано оно здесь.
 *
 * - **времени не существует** (перевод вперёд) → сдвигаем **вперёд** до ближайшего
 *   существующего; им ровно и является момент перевода часов;
 * - **время существует дважды** (перевод назад) → берём **первое** вхождение, то есть до
 *   перевода.
 *
 * Названное правило вместо `ZonedDateTime.of`, который делает то же самое молча: перенос лечения
 * на другой час — заметное для человека решение, и оно должно читаться в коде, а не выводиться
 * из документации платформы. Второе следствие — правило проверяется тестом на конкретный момент,
 * а не «как получится у библиотеки».
 */
private fun resolveDaylightSaving(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
    val local = LocalDateTime.of(date, time)
    val offsets = zone.rules.getValidOffsets(local)
    return when {
        offsets.isEmpty() -> zone.rules.getTransition(local).instant
        else -> local.toInstant(offsets.first())
    }
}
