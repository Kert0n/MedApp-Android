package com.kert0n.medapp.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryDateTest {

    @Test
    fun printedMonthBecomesItsLastDay() {
        // На упаковках печатают «03.2027». Первое марта было бы потерей почти целого месяца
        // годности, и человек видит развёрнутую дату до сохранения.
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDate.parse("03.2027").getOrThrow())
    }

    @Test
    fun leapFebruaryGetsItsTwentyNinth() {
        assertEquals(LocalDate.of(2028, 2, 29), ExpiryDate.parse("02.2028").getOrThrow())
        assertEquals(LocalDate.of(2027, 2, 28), ExpiryDate.parse("02.2027").getOrThrow())
    }

    @Test
    fun fullDatesAreTakenAsTheyAre() {
        val expected = LocalDate.of(2027, 3, 31)
        assertEquals(expected, ExpiryDate.parse("31.03.2027").getOrThrow())
        assertEquals(expected, ExpiryDate.parse("31/03/2027").getOrThrow())
        assertEquals(expected, ExpiryDate.parse("2027-03-31").getOrThrow())
    }

    @Test
    fun singleDigitMonthAndDayAreAccepted() {
        // На упаковках печатают и «3.2027», и «03.2027».
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDate.parse("3.2027").getOrThrow())
        assertEquals(LocalDate.of(2027, 3, 1), ExpiryDate.parse("1.3.2027").getOrThrow())
    }

    @Test
    fun leapYearRulesComeFromTheCalendarAndNotFromUs() {
        // 2000 — високосный, 1900 — нет, хотя оба делятся на четыре и на сто.
        assertEquals(LocalDate.of(2000, 2, 29), ExpiryDate.parse("02.2000").getOrThrow())
        assertEquals(LocalDate.of(1900, 2, 28), ExpiryDate.parse("02.1900").getOrThrow())
        assertEquals(ExpiryDateFormatReason.IMPOSSIBLE_DATE, reasonOf("29.02.1900"))
    }

    @Test
    fun isoMonthIsAlsoAMonth() {
        assertEquals(LocalDate.of(2027, 3, 31), ExpiryDate.parse("2027-03").getOrThrow())
    }

    @Test
    fun alreadyExpiredInputIsAccepted() {
        // ТЗ 4.1.2: «реалистично некорректные» данные принимаются и отрабатываются.
        assertEquals(LocalDate.of(2001, 1, 31), ExpiryDate.parse("01.2001").getOrThrow())
    }

    @Test
    fun impossibleDatesAreNamedAsSuch() {
        assertEquals(ExpiryDateFormatReason.IMPOSSIBLE_DATE, reasonOf("13.2027"))
        assertEquals(ExpiryDateFormatReason.IMPOSSIBLE_DATE, reasonOf("32.03.2027"))
        assertEquals(ExpiryDateFormatReason.IMPOSSIBLE_DATE, reasonOf("29.02.2027"))
    }

    @Test
    fun unrecognisedInputIsNamedAsSuch() {
        assertEquals(ExpiryDateFormatReason.UNKNOWN_FORMAT, reasonOf("2027"))
        assertEquals(ExpiryDateFormatReason.UNKNOWN_FORMAT, reasonOf("март 2027"))
        assertEquals(ExpiryDateFormatReason.UNKNOWN_FORMAT, reasonOf("03.27"))
    }

    @Test
    fun emptyInputIsNamedAsSuch() {
        assertEquals(ExpiryDateFormatReason.EMPTY, reasonOf("   "))
    }

    private fun reasonOf(input: String): ExpiryDateFormatReason {
        val error = ExpiryDate.parse(input).exceptionOrNull()
        assertTrue("ожидался отказ на «$input»", error is ExpiryDateFormatException)
        return (error as ExpiryDateFormatException).formatReason
    }
}
