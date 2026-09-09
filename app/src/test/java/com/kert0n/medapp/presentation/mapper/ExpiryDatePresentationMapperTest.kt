package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.presentation.dto.ExpiryDatePresentationDTO

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryDatePresentationMapperTest {

    @Test
    fun printedMonthBecomesItsLastDay() {
        // На упаковках печатают «03.2027». Первое марта было бы потерей почти целого месяца
        // годности, и человек видит развёрнутую дату до сохранения.
        assertEquals(LocalDate.of(2027, 3, 31), requireNotNull(mapped("03.2027").valueOrNull))
    }

    @Test
    fun leapFebruaryGetsItsTwentyNinth() {
        assertEquals(LocalDate.of(2028, 2, 29), requireNotNull(mapped("02.2028").valueOrNull))
        assertEquals(LocalDate.of(2027, 2, 28), requireNotNull(mapped("02.2027").valueOrNull))
    }

    @Test
    fun fullDatesAreTakenAsTheyAre() {
        val expected = LocalDate.of(2027, 3, 31)
        assertEquals(expected, requireNotNull(mapped("31.03.2027").valueOrNull))
        assertEquals(expected, requireNotNull(mapped("31/03/2027").valueOrNull))
        assertEquals(expected, requireNotNull(mapped("2027-03-31").valueOrNull))
    }

    @Test
    fun singleDigitMonthAndDayAreAccepted() {
        // На упаковках печатают и «3.2027», и «03.2027».
        assertEquals(LocalDate.of(2027, 3, 31), requireNotNull(mapped("3.2027").valueOrNull))
        assertEquals(LocalDate.of(2027, 3, 1), requireNotNull(mapped("1.3.2027").valueOrNull))
    }

    @Test
    fun leapYearRulesComeFromTheCalendarAndNotFromUs() {
        // 2000 — високосный, 1900 — нет, хотя оба делятся на четыре и на сто.
        assertEquals(LocalDate.of(2000, 2, 29), requireNotNull(mapped("02.2000").valueOrNull))
        assertEquals(LocalDate.of(1900, 2, 28), requireNotNull(mapped("02.1900").valueOrNull))
        assertEquals(ExpiryDatePresentationError.IMPOSSIBLE_DATE, errorOf("29.02.1900"))
    }

    @Test
    fun isoMonthIsAlsoAMonth() {
        assertEquals(LocalDate.of(2027, 3, 31), requireNotNull(mapped("2027-03").valueOrNull))
    }

    @Test
    fun alreadyExpiredInputIsAccepted() {
        // ТЗ 4.1.2: «реалистично некорректные» данные принимаются и отрабатываются.
        assertEquals(LocalDate.of(2001, 1, 31), requireNotNull(mapped("01.2001").valueOrNull))
    }

    @Test
    fun impossibleDatesAreNamedAsSuch() {
        assertEquals(ExpiryDatePresentationError.IMPOSSIBLE_DATE, errorOf("13.2027"))
        assertEquals(ExpiryDatePresentationError.IMPOSSIBLE_DATE, errorOf("32.03.2027"))
        assertEquals(ExpiryDatePresentationError.IMPOSSIBLE_DATE, errorOf("29.02.2027"))
    }

    @Test
    fun unrecognisedInputIsNamedAsSuch() {
        assertEquals(ExpiryDatePresentationError.UNKNOWN_FORMAT, errorOf("2027"))
        assertEquals(ExpiryDatePresentationError.UNKNOWN_FORMAT, errorOf("март 2027"))
        assertEquals(ExpiryDatePresentationError.UNKNOWN_FORMAT, errorOf("03.27"))
    }

    @Test
    fun emptyInputIsNamedAsSuch() {
        assertEquals(ExpiryDatePresentationError.EMPTY, errorOf("   "))
    }

    private fun mapped(input: String) = ExpiryDatePresentationDTO(input).toDomain()

    private fun errorOf(input: String): ExpiryDatePresentationError {
        val error = mapped(input).errorOrNull
        assertTrue("ожидался отказ на «$input»", error != null)
        return requireNotNull(error)
    }
}
