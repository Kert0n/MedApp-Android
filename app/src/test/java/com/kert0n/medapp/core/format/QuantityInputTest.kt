package com.kert0n.medapp.core.format

import com.kert0n.medapp.domain.model.TABLETS
import com.kert0n.medapp.domain.model.tablets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ввода не бросает: ввод — обычное состояние формы, а не сбой программы
 * (ТЗ 4.3, PLAN J4 REQ-051). Причина названа значением, а не текстом: текст — работа экрана.
 */
class QuantityInputTest {

    private fun errorOf(input: String): QuantityInputError {
        val error = QuantityInput.parse(input, TABLETS).exceptionOrNull()
        assertTrue("ожидался отказ на «$input»", error is QuantityInputException)
        return (error as QuantityInputException).error
    }

    @Test
    fun commaIsAcceptedBecauseKeyboardsPrintIt() {
        assertEquals(tablets("0.5"), QuantityInput.parse("0,5", TABLETS).getOrThrow())
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals(tablets("2"), QuantityInput.parse("  2  ", TABLETS).getOrThrow())
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(QuantityInputError.EMPTY, errorOf("   "))
    }

    @Test
    fun exponentIsRejected() {
        assertEquals(QuantityInputError.NOT_A_DECIMAL, errorOf("1e3"))
    }

    @Test
    fun signIsRejected() {
        assertEquals(QuantityInputError.NOT_A_DECIMAL, errorOf("-1"))
        assertEquals(QuantityInputError.NOT_A_DECIMAL, errorOf("+1"))
    }

    @Test
    fun sevenFractionDigitsAreRejected() {
        assertEquals(QuantityInputError.TOO_MANY_FRACTION_DIGITS, errorOf("0.0000001"))
    }

    @Test
    fun fourteenIntegerDigitsAreRejected() {
        assertEquals(QuantityInputError.TOO_MANY_INTEGER_DIGITS, errorOf("12345678901234"))
    }

    @Test
    fun inputLongerThanTheFieldIsRejectedBeforeParsing() {
        assertEquals(QuantityInputError.TOO_LONG, errorOf("1".repeat(64)))
    }

    @Test
    fun longestMeaningfulInputIsAccepted() {
        val longest = "1234567890123.123456"
        assertTrue(longest.length <= QUANTITY_MAX_INPUT_LENGTH)
        assertEquals(tablets(longest), QuantityInput.parse(longest, TABLETS).getOrThrow())
    }

    @Test
    fun overlongInputIsRejectedByItsRealFault() {
        // Предел длины на один символ больше самого длинного осмысленного ввода: он отсекает
        // мусор, а разряды у ввода правильной формы считаются точнее и называются точнее.
        assertEquals(
            QuantityInputError.TOO_MANY_INTEGER_DIGITS,
            errorOf("01234567890123.123456")
        )
    }
}
