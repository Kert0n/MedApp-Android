package com.kert0n.medapp.core.format

import com.kert0n.medapp.domain.model.QUANTITY_MAX_INTEGER_DIGITS
import com.kert0n.medapp.domain.model.QUANTITY_SCALE
import com.kert0n.medapp.domain.model.Quantity
import java.math.BigDecimal
import kotlin.uuid.Uuid

/** 13 разрядов, точка и 6 знаков, плюс запас на ведущий ноль. */
const val QUANTITY_MAX_INPUT_LENGTH = 21

/**
 * Почему введённая строка не стала количеством.
 *
 * Причина, а не текст: сообщение человеку — работа экрана, и оно живёт в `R.string.*` (PLAN H1).
 * Иначе адаптер ввода начал бы решать, на каком языке говорит приложение.
 */
enum class QuantityInputError {
    EMPTY,
    TOO_LONG,
    NOT_A_DECIMAL,
    TOO_MANY_FRACTION_DIGITS,
    TOO_MANY_INTEGER_DIGITS,
    OUT_OF_DOMAIN_RANGE
}

class QuantityInputException(val error: QuantityInputError) : IllegalArgumentException(error.name)

/** Шаблон — сам контракт B2: сервер отвергнет знак и экспоненту ровно так же. */
private val DECIMAL_INPUT = Regex("""^\d+(\.\d+)?$""")

/**
 * Превращение введённой строки в количество.
 *
 * Живёт в адаптере ввода, а не в домене: домен требует готовую величину и не должен знать, что
 * на клавиатуре бывает запятая, что поле имеет предельную длину и что вставленную из буфера
 * простыню надо отсечь до разбора. Домен отвечает за другое — допустимо ли **полученное**
 * значение, и это проверяет уже сам [Quantity].
 */
object QuantityInput {

    fun parse(input: String, unitId: Uuid): Result<Quantity> {
        val text = input.trim().replace(',', '.')
        val error = reject(text)
        if (error != null) return Result.failure(QuantityInputException(error))
        // Последнее слово за величиной: адаптер знает её нынешние пределы, но менять их вправе
        // домен, и тогда отказ должен остаться отказом, а не исключением наружу.
        return runCatching { Quantity(BigDecimal(text), unitId) }.recoverCatching {
            throw QuantityInputException(QuantityInputError.OUT_OF_DOMAIN_RANGE)
        }
    }

    private fun reject(text: String): QuantityInputError? {
        if (text.isEmpty()) return QuantityInputError.EMPTY
        if (text.length > QUANTITY_MAX_INPUT_LENGTH) return QuantityInputError.TOO_LONG
        if (!DECIMAL_INPUT.matches(text)) return QuantityInputError.NOT_A_DECIMAL
        val dot = text.indexOf('.')
        val integerDigits = if (dot < 0) text.length else dot
        val fractionDigits = if (dot < 0) 0 else text.length - dot - 1
        if (fractionDigits > QUANTITY_SCALE) return QuantityInputError.TOO_MANY_FRACTION_DIGITS
        if (integerDigits > QUANTITY_MAX_INTEGER_DIGITS) {
            return QuantityInputError.TOO_MANY_INTEGER_DIGITS
        }
        return null
    }
}
