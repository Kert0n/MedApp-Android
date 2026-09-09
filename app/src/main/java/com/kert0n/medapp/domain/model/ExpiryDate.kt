package com.kert0n.medapp.domain.model

import java.time.LocalDate
import java.time.YearMonth

enum class ExpiryDateFormatReason(internal val reason: String) {
    EMPTY("срок годности не введён"),
    UNKNOWN_FORMAT("ожидается «03.2027», «31.03.2027» или «2027-03-31»"),
    IMPOSSIBLE_DATE("такой даты не существует")
}

class ExpiryDateFormatException(val formatReason: ExpiryDateFormatReason) :
    IllegalArgumentException(formatReason.reason)

private val MONTH = Regex("""^(\d{1,2})[./](\d{4})$""")
private val MONTH_ISO = Regex("""^(\d{4})-(\d{1,2})$""")
private val DAY = Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{4})$""")
private val DAY_ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")

/**
 * Разбор введённого срока годности.
 *
 * Дата включительная: пачка, годная «до 31 марта», просрочена с 1 апреля — сравнение живёт в
 * [Package.isExpiredOn], а здесь только превращение ввода в дату.
 *
 * **Один месяц разворачивается в последний день месяца.** На упаковках печатают «03.2027», и
 * хранить приблизительное значение молча нельзя: экран показывает получившуюся дату до
 * сохранения, поэтому человек видит, что «03.2027» стало 31 марта, а не 1 марта (PLAN D3).
 *
 * Прошедшая дата принимается: ТЗ 4.1.2 прямо требует принимать «реалистично некорректные»
 * данные и отрабатывать их, а просрочка сама по себе ничего не делает (PLAN D3).
 */
object ExpiryDate {

    fun parse(input: String): Result<LocalDate> {
        val text = input.trim()
        if (text.isEmpty()) return failure(ExpiryDateFormatReason.EMPTY)

        MONTH.matchEntire(text)?.destructured?.let { (month, year) ->
            return endOfMonth(year.toInt(), month.toInt())
        }
        MONTH_ISO.matchEntire(text)?.destructured?.let { (year, month) ->
            return endOfMonth(year.toInt(), month.toInt())
        }
        DAY.matchEntire(text)?.destructured?.let { (day, month, year) ->
            return exactDay(year.toInt(), month.toInt(), day.toInt())
        }
        DAY_ISO.matchEntire(text)?.destructured?.let { (year, month, day) ->
            return exactDay(year.toInt(), month.toInt(), day.toInt())
        }
        return failure(ExpiryDateFormatReason.UNKNOWN_FORMAT)
    }

    private fun endOfMonth(year: Int, month: Int): Result<LocalDate> =
        runCatching { YearMonth.of(year, month).atEndOfMonth() }
            .recoverCatching { throw impossible() }

    private fun exactDay(year: Int, month: Int, day: Int): Result<LocalDate> =
        runCatching { LocalDate.of(year, month, day) }
            .recoverCatching { throw impossible() }

    private fun impossible() =
        ExpiryDateFormatException(ExpiryDateFormatReason.IMPOSSIBLE_DATE)

    private fun failure(reason: ExpiryDateFormatReason): Result<LocalDate> =
        Result.failure(ExpiryDateFormatException(reason))
}
