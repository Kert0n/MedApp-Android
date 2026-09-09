package com.kert0n.medapp.core.format

import com.kert0n.medapp.domain.model.DEFAULT_CURRENCY
import com.kert0n.medapp.domain.model.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale

const val MONEY_MAX_INPUT_LENGTH = 16

enum class MoneyInputError {
    EMPTY,
    TOO_LONG,
    NOT_A_DECIMAL,
    OUT_OF_CURRENCY_RANGE
}

class MoneyInputException(val error: MoneyInputError) : IllegalArgumentException(error.name)

/**
 * Грамматика проверяется **до** `DecimalFormat`, а не вместо него.
 *
 * `DecimalFormat.parse` читает знак и экспоненту независимо от шаблона: разделитель экспоненты
 * берётся из символов локали и по умолчанию заглавный. Поэтому «1E3» разбирался в тысячу, «1E-2» —
 * в копейку, а «-0» проходил как ноль, хотя обещано число без знака и экспоненты. Шаблон здесь и
 * есть это обещание.
 */
private val DECIMAL_INPUT = Regex("""^\d+([.,]\d+)?$""")

/**
 * Превращение введённой строки в цену.
 *
 * `DecimalFormat` **изменяем и не потокобезопасен**, поэтому создаётся на каждый вызов, а не
 * хранится общим экземпляром: на восьми потоках общий экземпляр выдавал из «7890.12» число
 * «778899001122» и бросал исключения мимо `Result`. Разбор целиком идёт внутри `runCatching`,
 * чтобы наружу не вышло ничего, кроме результата.
 */
object MoneyInput {

    fun parse(input: String, currency: Currency = DEFAULT_CURRENCY): Result<Money> = runCatching {
        val text = input.trim()
        if (text.isEmpty()) throw MoneyInputException(MoneyInputError.EMPTY)
        if (text.length > MONEY_MAX_INPUT_LENGTH) {
            throw MoneyInputException(MoneyInputError.TOO_LONG)
        }
        if (!DECIMAL_INPUT.matches(text)) throw MoneyInputException(MoneyInputError.NOT_A_DECIMAL)

        val separator = if (text.contains(',')) ',' else '.'
        val amount = decimalFormat(separator).parseFully(text)
            ?: throw MoneyInputException(MoneyInputError.NOT_A_DECIMAL)
        try {
            Money(amount, currency)
        } catch (cause: IllegalArgumentException) {
            throw MoneyInputException(MoneyInputError.OUT_OF_CURRENCY_RANGE)
        }
    }

    /**
     * Свой экземпляр на вызов. Групповой разделитель задан явно и отличается от дробного: иначе
     * запятая означала бы сразу и то, и другое.
     */
    private fun decimalFormat(separator: Char): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            decimalSeparator = separator
            groupingSeparator = if (separator == '.') ',' else '.'
        }
        return DecimalFormat("0", symbols).apply {
            isParseBigDecimal = true
            isGroupingUsed = false
        }
    }

    /**
     * Разобран должен быть весь ввод: `DecimalFormat` останавливается на первом непонятном
     * символе и молча отдаёт прочитанное.
     */
    private fun DecimalFormat.parseFully(text: String): BigDecimal? {
        val position = ParsePosition(0)
        val parsed = parse(text, position) as? BigDecimal ?: return null
        return parsed.takeIf { position.index == text.length }
    }
}
