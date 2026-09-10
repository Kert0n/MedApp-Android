package com.kert0n.medapp.domain.course

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Objects

/**
 * Календарное намерение человека: с какого по какое число, в какие дни недели, в какое время и
 * в какой зоне. Величина: другой набор времён — другое расписание, а у действующего курса оно
 * неизменно (PLAN D5). Дата конца включительная; «в девять утра» — девять утра своей зоны, а не
 * системной, поэтому перелёт лечение не сдвигает.
 *
 * Точность — минута: секунды в назначении не значат ничего, и допускать их здесь значило бы
 * называть разными расписания, которые описывают один и тот же приём.
 */
class CourseSchedule(
    val start: LocalDate,
    val endInclusive: LocalDate,
    daysOfWeek: Set<DayOfWeek>,
    times: List<LocalTime>,
    val zone: ZoneId
) {

    /**
     * Свои копии, а не переданные коллекции: `val` защищает ссылку, а не содержимое, и список,
     * оставшийся у вызывающего, менял бы расписание действующего курса — вместе с назначением в
     * записи эпизода, где оно неизменно по определению (PLAN D5).
     */
    val daysOfWeek: Set<DayOfWeek> = daysOfWeek.toSet()

    val times: List<LocalTime> = times.toList()

    init {
        require(!endInclusive.isBefore(start)) {
            "конец расписания не бывает раньше начала: $start — $endInclusive"
        }
        // Пустая маска дней — расписание без приёмов, а не «каждый день».
        require(daysOfWeek.isNotEmpty()) { "расписание без дней недели не порождает приёмов" }
        require(times.isNotEmpty()) { "расписание без времён не порождает приёмов" }
        require(times.distinct().size == times.size) {
            "одно и то же время дважды — это один приём, а не два"
        }
        require(times == times.sorted()) { "времена хранятся по возрастанию" }
        // Лекарство принимают в 09:00, а не в 09:00:10: точность расписания — минута, и это
        // правило самого расписания. Иначе два времени, различных здесь, стали бы одним ниже.
        require(times.all { it.second == 0 && it.nano == 0 }) {
            "время приёма называется с точностью до минуты: $times"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is CourseSchedule &&
                start == other.start &&
                endInclusive == other.endInclusive &&
                daysOfWeek == other.daysOfWeek &&
                times == other.times &&
                zone == other.zone
            )

    override fun hashCode(): Int = Objects.hash(start, endInclusive, daysOfWeek, times, zone)

    override fun toString(): String =
        "CourseSchedule($start — $endInclusive, $daysOfWeek, $times, $zone)"

    /**
     * Сколько пунктов порождает расписание целиком. Считается арифметикой по неделям, а не
     * обходом дней, — чтобы отвергнуть слишком большой план до материализации (PLAN F4).
     */
    fun occurrenceCount(): Int {
        val totalDays = ChronoUnit.DAYS.between(start, endInclusive) + 1
        val fullWeeks = totalDays / 7
        val remainder = (totalDays % 7).toInt()
        val tailDays = (0 until remainder).count {
            start.plusDays(it.toLong()).dayOfWeek in daysOfWeek
        }
        val occurrences = (fullWeeks * daysOfWeek.size + tailDays) * times.size
        require(occurrences <= Int.MAX_VALUE) { "расписание такого размера не материализуется" }
        return occurrences.toInt()
    }

    /**
     * Пункты, чей момент попадает в `[from, until)`, по возрастанию момента. Соседние окна
     * стыкуются без повтора и без дыры; длину окна выбирает вызывающий (PLAN F4).
     */
    fun occurrences(from: Instant, until: Instant): List<ScheduledOccurrence> {
        require(!until.isBefore(from)) { "интервал [from, until) не бывает обратным" }
        if (until == from) return emptyList()
        // Сутки запаса с каждой стороны: момент зависит от перехода часов, поэтому отбор идёт по
        // моменту, а не по дате. `atZone().toLocalDate()` — потому что `LocalDate.ofInstant`
        // появился только в API 34.
        val firstDate = maxOf(start, from.atZone(zone).toLocalDate().minusDays(1))
        val lastDate = minOf(endInclusive, until.atZone(zone).toLocalDate())
        if (lastDate.isBefore(firstDate)) return emptyList()

        val found = ArrayList<ScheduledOccurrence>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            if (date.dayOfWeek in daysOfWeek) {
                for (time in times) {
                    val at = momentOf(date, time)
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
     * Сколько пунктов от [from] до конца календаря ещё ждут ответа. Считается календарём, а не
     * строками окна материализации: иначе годовой курс видел бы только шестьдесят дней
     * потребности. [resolved] — отвеченные пункты по исходным дате и времени (PLAN F4);
     * просроченные неотвеченные попадают в подсчёт выбором [from].
     */
    fun countRemaining(from: Instant, resolved: Set<Pair<LocalDate, LocalTime>>): Int {
        // Сутки запаса: последний пункт мог сдвинуться вперёд переходом часов.
        val until = endInclusive.plusDays(2).atStartOfDay(zone).toInstant()
        if (!until.isAfter(from)) return 0
        return occurrences(from, until).count { (it.localDate to it.localTime) !in resolved }
    }

    /**
     * Момент назначенного времени в зоне расписания. Несуществующее время (перевод вперёд)
     * сдвигается к моменту перевода; время, которое бывает дважды (перевод назад), берётся
     * первым. Правило записано явно, а не доверено `ZonedDateTime.of`, потому что сдвиг приёма
     * на другой час человек заметит.
     */
    private fun momentOf(date: LocalDate, time: LocalTime): Instant {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.isEmpty() -> zone.rules.getTransition(local).instant
            else -> local.toInstant(offsets.first())
        }
    }
}
