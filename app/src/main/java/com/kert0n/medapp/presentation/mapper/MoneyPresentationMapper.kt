package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.Money
import com.kert0n.medapp.presentation.dto.MoneyPresentationDTO
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale

const val MONEY_MAX_INPUT_LENGTH = 16

enum class MoneyPresentationError {
    EMPTY,
    TOO_LONG,
    NOT_A_DECIMAL,
    UNKNOWN_CURRENCY,
    OUT_OF_CURRENCY_RANGE
}

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
 * Приведение введённой цены к домену.
 *
 * `DecimalFormat` **изменяем и не потокобезопасен**, поэтому создаётся на каждый вызов, а не
 * хранится общим экземпляром: на восьми потоках общий экземпляр выдавал из «7890.12» число
 * «778899001122» и бросал исключения мимо результата.
 */
fun MoneyPresentationDTO.toDomain(): PresentationMapping<Money, MoneyPresentationError> {
    val text = amount.trim()
    if (text.isEmpty()) return rejected(MoneyPresentationError.EMPTY)
    if (text.length > MONEY_MAX_INPUT_LENGTH) return rejected(MoneyPresentationError.TOO_LONG)
    if (!DECIMAL_INPUT.matches(text)) return rejected(MoneyPresentationError.NOT_A_DECIMAL)

    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        ?: return rejected(MoneyPresentationError.UNKNOWN_CURRENCY)

    val separator = if (text.contains(',')) ',' else '.'
    val parsed = decimalFormat(separator).parseFully(text)
        ?: return rejected(MoneyPresentationError.NOT_A_DECIMAL)

    return runCatching { Money(parsed, currency) }.fold(
        onSuccess = { PresentationMapping.Mapped(it) },
        onFailure = { rejected(MoneyPresentationError.OUT_OF_CURRENCY_RANGE) }
    )
}

private fun rejected(
    error: MoneyPresentationError
): PresentationMapping<Money, MoneyPresentationError> = PresentationMapping.Rejected(error)

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
 * Разобран должен быть весь ввод: `DecimalFormat` останавливается на первом непонятном символе
 * и молча отдаёт прочитанное.
 */
private fun DecimalFormat.parseFully(text: String): BigDecimal? {
    val position = ParsePosition(0)
    val parsed = parse(text, position) as? BigDecimal ?: return null
    return parsed.takeIf { position.index == text.length }
}
