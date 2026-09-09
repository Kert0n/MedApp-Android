package com.kert0n.medapp.domain.model

import java.text.ParsePosition
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class ExpiryDateFormatReason(internal val reason: String) {
    EMPTY("срок годности не введён"),
    UNKNOWN_FORMAT("ожидается «03.2027», «31.03.2027» или «2027-03-31»"),
    IMPOSSIBLE_DATE("такой даты не существует")
}

class ExpiryDateFormatException(val formatReason: ExpiryDateFormatReason) :
    IllegalArgumentException(formatReason.reason)

/**
 * `uuuu`, а не `yyyy`: строгое разрешение требует год пролептического календаря, иначе оно
 * потребовало бы ещё и эру. `M` и `d` принимают и одну цифру, и две — на упаковках печатают
 * и «3.2027», и «03.2027».
 */
private val MONTH_FORMATS = listOf("M.uuuu", "M/uuuu", "uuuu-MM").map(::strictFormat)

private val DAY_FORMATS = listOf("d.M.uuuu", "d/M/uuuu", "uuuu-MM-dd").map(::strictFormat)

private fun strictFormat(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)

/**
 * Разбор введённого срока годности.
 *
 * Дата включительная: пачка, годная «до 31 марта», просрочена с 1 апреля — сравнение живёт в
 * [Package.isExpiredOn], а здесь только превращение ввода в дату.
 *
 * **Один месяц разворачивается в последний день месяца.** На упаковках печатают «03.2027», и
 * хранить приблизительное значение молча нельзя: экран показывает получившуюся дату до
 * сохранения, поэтому человек видит, что «03.2027» стало 31 марта, а не 1 марта (PLAN D3).
 * Последний день месяца называет [YearMonth.atEndOfMonth] — високосные годы знает календарь,
 * а не мы.
 *
 * Прошедшая дата принимается: ТЗ 4.1.2 прямо требует принимать «реалистично некорректные»
 * данные и отрабатывать их, а просрочка сама по себе ничего не делает (PLAN D3).
 */
object ExpiryDate {

    fun parse(input: String): Result<LocalDate> {
        val text = input.trim()
        if (text.isEmpty()) return failure(ExpiryDateFormatReason.EMPTY)

        for (format in MONTH_FORMATS) {
            if (!matchesShape(text, format)) continue
            return resolve { YearMonth.parse(text, format).atEndOfMonth() }
        }
        for (format in DAY_FORMATS) {
            if (!matchesShape(text, format)) continue
            return resolve { LocalDate.parse(text, format) }
        }
        return failure(ExpiryDateFormatReason.UNKNOWN_FORMAT)
    }

    /**
     * Совпал ли ввод с шаблоном по форме, без проверки самих значений.
     *
     * [DateTimeFormatter.parseUnresolved] разбирает поля, но не сводит их в дату, поэтому
     * тринадцатый месяц на этом шаге ещё проходит. Так и различаются два разных отказа:
     * «март 2027» не похож на дату вовсе, а «13.2027» похож, но такой даты не существует —
     * и человеку это стоит сказать разными словами.
     */
    private fun matchesShape(text: String, format: DateTimeFormatter): Boolean {
        val position = ParsePosition(0)
        format.parseUnresolved(text, position) ?: return false
        return position.index == text.length
    }

    private fun resolve(parse: () -> LocalDate): Result<LocalDate> =
        runCatching(parse).recoverCatching {
            throw ExpiryDateFormatException(ExpiryDateFormatReason.IMPOSSIBLE_DATE)
        }

    private fun failure(reason: ExpiryDateFormatReason): Result<LocalDate> =
        Result.failure(ExpiryDateFormatException(reason))
}
