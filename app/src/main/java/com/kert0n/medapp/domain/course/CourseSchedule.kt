package com.kert0n.medapp.domain.course

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Календарное намерение человека: с какого по какое число, в какие дни недели и в какое время.
 *
 * **Величина, а не сущность.** Другой набор времён — другое расписание, а не изменённое: у
 * действующего курса расписание неизменно, и замена лечения означает новый курс (PLAN D5).
 *
 * **Зона своя, а не системная.** Перелёт не сдвигает назначенное лечение молча: «в девять утра»
 * остаётся девятью утра того часового пояса, в котором лечение назначено. Смена зоны — часть
 * нового расписания, а значит нового курса.
 *
 * Дата конца **включительная**: «по тридцать первое» значит, что тридцать первое входит.
 * Разрешение перехода на летнее время здесь не живёт — это вычисление, а не намерение: одно
 * правило на весь проект лежит в `domain/calc/schedule`.
 */
data class CourseSchedule(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val daysOfWeek: Set<DayOfWeek>,
    val times: List<LocalTime>,
    val zone: ZoneId
) {

    init {
        require(!endInclusive.isBefore(start)) {
            "конец расписания не бывает раньше начала: $start — $endInclusive"
        }
        // Пустая маска дней — это не «каждый день», а расписание без единого приёма. Молча
        // подставлять «все дни» значило бы придумать лечение за человека.
        require(daysOfWeek.isNotEmpty()) { "расписание без дней недели не порождает приёмов" }
        require(times.isNotEmpty()) { "расписание без времён не порождает приёмов" }
        require(times.distinct().size == times.size) {
            "одно и то же время дважды — это один приём, а не два"
        }
        require(times == times.sorted()) { "времена хранятся по возрастанию" }
    }

    /**
     * Сколько пунктов порождает расписание целиком.
     *
     * Считается арифметикой, а не обходом дней: календарь из полных недель и остатка даёт ответ
     * за семь проверок, сколько бы лет ни длился курс. Число нужно до материализации — окно
     * в шестьдесят дней (PLAN F4) не знает полного размера плана, а слишком большой план
     * отвергается с объяснением, а не материализуется наполовину.
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
}
