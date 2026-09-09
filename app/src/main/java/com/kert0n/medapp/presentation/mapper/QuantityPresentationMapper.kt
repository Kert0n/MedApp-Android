package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.QUANTITY_MAX_INTEGER_DIGITS
import com.kert0n.medapp.domain.model.QUANTITY_SCALE
import com.kert0n.medapp.domain.model.Quantity
import com.kert0n.medapp.presentation.dto.QuantityPresentationDTO
import java.math.BigDecimal

/** 13 разрядов, точка и 6 знаков, плюс запас на ведущий ноль. Свойство поля, не величины. */
const val QUANTITY_MAX_INPUT_LENGTH = 21

enum class QuantityPresentationError {
    EMPTY,
    TOO_LONG,
    NOT_A_DECIMAL,
    TOO_MANY_FRACTION_DIGITS,
    TOO_MANY_INTEGER_DIGITS,
    OUT_OF_DOMAIN_RANGE
}

/** Шаблон — сам контракт B2: сервер отвергнет знак и экспоненту ровно так же. */
private val DECIMAL_INPUT = Regex("""^\d+(\.\d+)?$""")

/**
 * Приведение введённого количества к домену.
 *
 * Домен требует готовую величину и не знает, что на клавиатуре бывает запятая, что у поля есть
 * предельная длина и что вставленную из буфера простыню надо отсечь до разбора. Всё это — свойства
 * ввода, и живут они здесь.
 */
fun QuantityPresentationDTO.toDomain(): PresentationMapping<Quantity, QuantityPresentationError> {
    val text = amount.trim().replace(',', '.')
    reject(text)?.let { return PresentationMapping.Rejected(it) }
    // Последнее слово за величиной: её нынешние пределы здесь известны, но менять их вправе домен,
    // и тогда отказ должен остаться отказом, а не исключением наружу.
    return runCatching { Quantity(BigDecimal(text), unitId) }
        .fold(
            onSuccess = { PresentationMapping.Mapped(it) },
            onFailure = {
                PresentationMapping.Rejected(QuantityPresentationError.OUT_OF_DOMAIN_RANGE)
            }
        )
}

private fun reject(text: String): QuantityPresentationError? {
    if (text.isEmpty()) return QuantityPresentationError.EMPTY
    if (text.length > QUANTITY_MAX_INPUT_LENGTH) return QuantityPresentationError.TOO_LONG
    if (!DECIMAL_INPUT.matches(text)) return QuantityPresentationError.NOT_A_DECIMAL
    val dot = text.indexOf('.')
    val integerDigits = if (dot < 0) text.length else dot
    val fractionDigits = if (dot < 0) 0 else text.length - dot - 1
    if (fractionDigits > QUANTITY_SCALE) return QuantityPresentationError.TOO_MANY_FRACTION_DIGITS
    if (integerDigits > QUANTITY_MAX_INTEGER_DIGITS) {
        return QuantityPresentationError.TOO_MANY_INTEGER_DIGITS
    }
    return null
}
