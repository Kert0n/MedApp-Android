package com.kert0n.medapp.core.format

import com.kert0n.medapp.domain.model.ExpiryDate
import java.text.ParsePosition
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class ExpiryDateInputError {
    EMPTY,
    UNKNOWN_FORMAT,
    IMPOSSIBLE_DATE
}

class ExpiryDateInputException(val error: ExpiryDateInputError) :
    IllegalArgumentException(error.name)

/**
 * `uuuu`, а не `yyyy`: строгое разрешение требует год пролептического календаря, иначе оно
 * потребовало бы ещё и эру. `M` и `d` принимают и одну цифру, и две — на упаковках печатают
 * и «3.2027», и «03.2027». `DateTimeFormatter` неизменяем и потокобезопасен, поэтому общие
 * экземпляры здесь безопасны, в отличие от `DecimalFormat`.
 */
private val MONTH_FORMATS = listOf("M.uuuu", "M/uuuu", "uuuu-MM").map(::strictFormat)

private val DAY_FORMATS = listOf("d.M.uuuu", "d/M/uuuu", "uuuu-MM-dd").map(::strictFormat)

private fun strictFormat(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)

/**
 * Превращение введённого срока годности в дату.
 *
 * Здесь **только распознавание записи**: какие шаблоны бывают на упаковке и что человек мог
 * напечатать. Продуктовое правило — что месяц означает его последний день — живёт в домене
 * ([ExpiryDate.lastDayOf]), потому что это решение о годности, а не о форме ввода. Разделение
 * ровно такое: «03.2027» распознаёт адаптер, последним днём марта его делает домен.
 *
 * Прошедшая дата принимается: ТЗ 4.1.2 прямо требует принимать «реалистично некорректные» данные
 * и отрабатывать их.
 */
object ExpiryDateInput {

    fun parse(input: String): Result<LocalDate> {
        val text = input.trim()
        if (text.isEmpty()) return failure(ExpiryDateInputError.EMPTY)

        for (format in MONTH_FORMATS) {
            if (!matchesShape(text, format)) continue
            return resolve { ExpiryDate.lastDayOf(YearMonth.parse(text, format)) }
        }
        for (format in DAY_FORMATS) {
            if (!matchesShape(text, format)) continue
            return resolve { LocalDate.parse(text, format) }
        }
        return failure(ExpiryDateInputError.UNKNOWN_FORMAT)
    }

    /**
     * Совпал ли ввод с шаблоном по форме, без проверки самих значений.
     *
     * [DateTimeFormatter.parseUnresolved] разбирает поля, но не сводит их в дату, поэтому
     * тринадцатый месяц на этом шаге ещё проходит. Так и различаются два разных отказа:
     * «март 2027» не похож на дату вовсе, а «13.2027» похож, но такой даты не существует.
     */
    private fun matchesShape(text: String, format: DateTimeFormatter): Boolean {
        val position = ParsePosition(0)
        format.parseUnresolved(text, position) ?: return false
        return position.index == text.length
    }

    private fun resolve(parse: () -> LocalDate): Result<LocalDate> =
        runCatching(parse).recoverCatching {
            throw ExpiryDateInputException(ExpiryDateInputError.IMPOSSIBLE_DATE)
        }

    private fun failure(error: ExpiryDateInputError): Result<LocalDate> =
        Result.failure(ExpiryDateInputException(error))
}
