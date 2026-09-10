package com.kert0n.medapp.presentation.value


import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.tablets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ввода не бросает: ввод — обычное состояние формы, а не сбой программы
 * (ТЗ 4.3, PLAN J4 REQ-051). Причина названа значением, а не текстом: текст — работа экрана.
 */
class QuantityPresentationMapperTest {

    private fun mapped(input: String) =
        QuantityPresentationDTO(input, TABLETS).toDomain()

    private fun errorOf(input: String): QuantityPresentationError {
        val error = mapped(input).errorOrNull
        assertTrue("ожидался отказ на «$input»", error != null)
        return requireNotNull(error)
    }

    @Test
    fun commaIsAcceptedBecauseKeyboardsPrintIt() {
        assertEquals(tablets("0.5"), requireNotNull(mapped("0,5").valueOrNull))
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals(tablets("2"), requireNotNull(mapped("  2  ").valueOrNull))
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(QuantityPresentationError.EMPTY, errorOf("   "))
    }

    @Test
    fun exponentIsRejected() {
        assertEquals(QuantityPresentationError.NOT_A_DECIMAL, errorOf("1e3"))
    }

    @Test
    fun signIsRejected() {
        assertEquals(QuantityPresentationError.NOT_A_DECIMAL, errorOf("-1"))
        assertEquals(QuantityPresentationError.NOT_A_DECIMAL, errorOf("+1"))
    }

    @Test
    fun sevenFractionDigitsAreRejected() {
        assertEquals(QuantityPresentationError.TOO_MANY_FRACTION_DIGITS, errorOf("0.0000001"))
    }

    @Test
    fun fourteenIntegerDigitsAreRejected() {
        assertEquals(QuantityPresentationError.TOO_MANY_INTEGER_DIGITS, errorOf("12345678901234"))
    }

    @Test
    fun inputLongerThanTheFieldIsRejectedBeforeParsing() {
        assertEquals(QuantityPresentationError.TOO_LONG, errorOf("1".repeat(64)))
    }

    @Test
    fun longestMeaningfulInputIsAccepted() {
        val longest = "1234567890123.123456"
        assertTrue(longest.length <= QUANTITY_MAX_INPUT_LENGTH)
        assertEquals(tablets(longest), requireNotNull(mapped(longest).valueOrNull))
    }

    @Test
    fun overlongInputIsRejectedByItsRealFault() {
        // Предел длины на один символ больше самого длинного осмысленного ввода: он отсекает
        // мусор, а разряды у ввода правильной формы считаются точнее и называются точнее.
        assertEquals(
            QuantityPresentationError.TOO_MANY_INTEGER_DIGITS,
            errorOf("01234567890123.123456")
        )
    }
}
