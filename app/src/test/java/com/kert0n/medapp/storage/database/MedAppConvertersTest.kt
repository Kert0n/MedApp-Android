package com.kert0n.medapp.storage.database

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Круговое преобразование односоставных значений: то, что записано в колонку, читается обратно
 * тем же самым (PLAN F3).
 */
class MedAppConvertersTest {

    @Test
    fun uuidKeepsCanonicalForm() {
        val id = Uuid.parse("00000000-0000-4000-8000-000000000021")
        assertEquals("00000000-0000-4000-8000-000000000021", MedAppConverters.uuidToStorage(id))
        assertEquals(id, MedAppConverters.uuidFromStorage(MedAppConverters.uuidToStorage(id)))
    }

    @Test
    fun instantKeepsMilliseconds() {
        val moment = Instant.parse("2026-03-31T21:00:00.123Z")
        assertEquals(moment.toEpochMilli(), MedAppConverters.instantToStorage(moment))
        assertEquals(moment, MedAppConverters.instantFromStorage(moment.toEpochMilli()))
    }

    @Test
    fun localDateKeepsIsoText() {
        val date = LocalDate.of(2027, 3, 31)
        assertEquals("2027-03-31", MedAppConverters.localDateToStorage(date))
        assertEquals(date, MedAppConverters.localDateFromStorage("2027-03-31"))
    }

    /** Полночь и последняя минута суток — те границы, на которых ошибается смещение на единицу. */
    @Test
    fun localTimeIsMinutesFromMidnightAtBothEnds() {
        assertEquals(0, MedAppConverters.localTimeToStorage(LocalTime.MIDNIGHT))
        assertEquals(1439, MedAppConverters.localTimeToStorage(LocalTime.of(23, 59)))
        assertEquals(LocalTime.MIDNIGHT, MedAppConverters.localTimeFromStorage(0))
        assertEquals(LocalTime.of(23, 59), MedAppConverters.localTimeFromStorage(1439))
    }

    @Test
    fun everyMinuteOfTheDaySurvivesTheRoundTrip() {
        for (minute in 0 until 24 * 60) {
            val time = LocalTime.ofSecondOfDay(minute.toLong() * 60)
            assertEquals(time, MedAppConverters.localTimeFromStorage(MedAppConverters.localTimeToStorage(time)))
        }
    }

    @Test
    fun zoneIdKeepsItsIdentifier() {
        val zone = ZoneId.of("Europe/Moscow")
        assertEquals("Europe/Moscow", MedAppConverters.zoneIdToStorage(zone))
        assertEquals(zone, MedAppConverters.zoneIdFromStorage("Europe/Moscow"))
    }

    /** Все 128 подмножеств: маска не имеет права перепутать два дня местами. */
    @Test
    fun everySetOfDaysSurvivesTheRoundTrip() {
        val days = DayOfWeek.entries
        for (mask in 0 until (1 shl days.size)) {
            val chosen = days.filterTo(LinkedHashSet()) { mask and (1 shl (it.value - 1)) != 0 }
            val stored = MedAppConverters.daysOfWeekToStorage(chosen)
            assertEquals(chosen, MedAppConverters.daysOfWeekFromStorage(stored))
        }
    }

    @Test
    fun mondayIsTheLowestBitAndSundayTheHighest() {
        assertEquals(1, MedAppConverters.daysOfWeekToStorage(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, MedAppConverters.daysOfWeekToStorage(setOf(DayOfWeek.SUNDAY)))
    }

    @Test
    fun absenceStaysAbsence() {
        assertNull(MedAppConverters.uuidToStorage(null))
        assertNull(MedAppConverters.uuidFromStorage(null))
        assertNull(MedAppConverters.instantFromStorage(null))
        assertNull(MedAppConverters.localDateFromStorage(null))
        assertNull(MedAppConverters.localTimeFromStorage(null))
        assertNull(MedAppConverters.zoneIdFromStorage(null))
        assertNull(MedAppConverters.daysOfWeekFromStorage(null))
    }
}
