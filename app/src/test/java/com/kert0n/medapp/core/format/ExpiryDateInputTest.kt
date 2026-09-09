package com.kert0n.medapp.core.format

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryDateInputTest {

    @Test
    fun printedMonthBecomesItsLastDay() {
        // На упаковках печатают «03.2027». Первое марта было бы потерей почти целого месяца
        // годности, и человек видит развёрнутую дату до сохранения.
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDateInput.parse("03.2027").getOrThrow())
    }

    @Test
    fun leapFebruaryGetsItsTwentyNinth() {
        assertEquals(LocalDate.of(2028, 2, 29), ExpiryDateInput.parse("02.2028").getOrThrow())
        assertEquals(LocalDate.of(2027, 2, 28), ExpiryDateInput.parse("02.2027").getOrThrow())
    }

    @Test
    fun fullDatesAreTakenAsTheyAre() {
        val expected = LocalDate.of(2027, 3, 31)
        assertEquals(expected, ExpiryDateInput.parse("31.03.2027").getOrThrow())
        assertEquals(expected, ExpiryDateInput.parse("31/03/2027").getOrThrow())
        assertEquals(expected, ExpiryDateInput.parse("2027-03-31").getOrThrow())
    }

    @Test
    fun singleDigitMonthAndDayAreAccepted() {
        // На упаковках печатают и «3.2027», и «03.2027».
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDateInput.parse("3.2027").getOrThrow())
        assertEquals(LocalDate.of(2027, 3, 1), ExpiryDateInput.parse("1.3.2027").getOrThrow())
    }

    @Test
    fun leapYearRulesComeFromTheCalendarAndNotFromUs() {
        // 2000 — високосный, 1900 — нет, хотя оба делятся на четыре и на сто.
        assertEquals(LocalDate.of(2000, 2, 29), ExpiryDateInput.parse("02.2000").getOrThrow())
        assertEquals(LocalDate.of(1900, 2, 28), ExpiryDateInput.parse("02.1900").getOrThrow())
        assertEquals(ExpiryDateInputError.IMPOSSIBLE_DATE, errorOf("29.02.1900"))
    }

    @Test
    fun isoMonthIsAlsoAMonth() {
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDateInput.parse("2027-03").getOrThrow())
    }

    @Test
    fun alreadyExpiredInputIsAccepted() {
        // ТЗ 4.1.2: «реалистично некорректные» данные принимаются и отрабатываются.
        assertEquals(LocalDate.of(2001, 1, 31), ExpiryDateInput.parse("01.2001").getOrThrow())
    }

    @Test
    fun impossibleDatesAreNamedAsSuch() {
        assertEquals(ExpiryDateInputError.IMPOSSIBLE_DATE, errorOf("13.2027"))
        assertEquals(ExpiryDateInputError.IMPOSSIBLE_DATE, errorOf("32.03.2027"))
        assertEquals(ExpiryDateInputError.IMPOSSIBLE_DATE, errorOf("29.02.2027"))
    }

    @Test
    fun unrecognisedInputIsNamedAsSuch() {
        assertEquals(ExpiryDateInputError.UNKNOWN_FORMAT, errorOf("2027"))
        assertEquals(ExpiryDateInputError.UNKNOWN_FORMAT, errorOf("март 2027"))
        assertEquals(ExpiryDateInputError.UNKNOWN_FORMAT, errorOf("03.27"))
    }

    @Test
    fun emptyInputIsNamedAsSuch() {
        assertEquals(ExpiryDateInputError.EMPTY, errorOf("   "))
    }

    private fun errorOf(input: String): ExpiryDateInputError {
        val error = ExpiryDateInput.parse(input).exceptionOrNull()
        assertTrue("ожидался отказ на «$input»", error is ExpiryDateInputException)
        return (error as ExpiryDateInputException).error
    }
}
