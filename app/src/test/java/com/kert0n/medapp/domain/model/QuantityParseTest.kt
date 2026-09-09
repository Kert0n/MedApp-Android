package com.kert0n.medapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ввода не бросает: ввод — обычное состояние формы, а не сбой программы
 * (ТЗ 4.3, PLAN J4 REQ-051). Причина отказа названа, чтобы экран объяснил её человеку.
 */
class QuantityParseTest {

    private fun reasonOf(input: String): QuantityFormatReason {
        val error = Quantity.parse(input, TABLETS).exceptionOrNull()
        assertTrue("ожидался отказ на «$input»", error is QuantityFormatException)
        return (error as QuantityFormatException).formatReason
    }

    @Test
    fun commaIsAcceptedBecauseKeyboardsPrintIt() {
        assertEquals(tablets("0.5"), Quantity.parse("0,5", TABLETS).getOrThrow())
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals(tablets("2"), Quantity.parse("  2  ", TABLETS).getOrThrow())
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(QuantityFormatReason.EMPTY, reasonOf("   "))
    }

    @Test
    fun exponentIsRejected() {
        assertEquals(QuantityFormatReason.NOT_A_DECIMAL, reasonOf("1e3"))
    }

    @Test
    fun signIsRejected() {
        assertEquals(QuantityFormatReason.NOT_A_DECIMAL, reasonOf("-1"))
        assertEquals(QuantityFormatReason.NOT_A_DECIMAL, reasonOf("+1"))
    }

    @Test
    fun sevenFractionDigitsAreRejected() {
        assertEquals(QuantityFormatReason.TOO_MANY_FRACTION_DIGITS, reasonOf("0.0000001"))
    }

    @Test
    fun fourteenIntegerDigitsAreRejected() {
        assertEquals(QuantityFormatReason.TOO_MANY_INTEGER_DIGITS, reasonOf("12345678901234"))
    }

    @Test
    fun inputLongerThanTheFieldIsRejectedBeforeParsing() {
        assertEquals(QuantityFormatReason.TOO_LONG, reasonOf("1".repeat(64)))
    }

    @Test
    fun longestMeaningfulInputIsAccepted() {
        val longest = "1234567890123.123456"
        assertTrue(longest.length <= QUANTITY_MAX_INPUT_LENGTH)
        assertEquals(tablets(longest), Quantity.parse(longest, TABLETS).getOrThrow())
    }

    @Test
    fun overlongInputIsRejectedByItsRealFault() {
        // Предел длины на один символ больше самого длинного осмысленного ввода: он отсекает
        // мусор, а разряды у ввода правильной формы считаются точнее и называются точнее.
        assertEquals(QuantityFormatReason.TOO_MANY_INTEGER_DIGITS, reasonOf("01234567890123.123456"))
    }
}
