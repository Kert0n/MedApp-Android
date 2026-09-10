package com.kert0n.medapp.storage.database

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Односоставные значения превращаются в одну колонку и обратно без потери разрядов (PLAN F3).
 *
 * Величины с единицей — `Quantity` и `Money` — сюда не попадают: у них две составляющие, а
 * конвертер даёт одну колонку и молча потерял бы единицу или валюту. Их раскладывают по паре
 * колонок мапперы хранения своего понятия.
 */
object MedAppConverters {

    @TypeConverter
    fun uuidToStorage(value: Uuid?): String? = value?.toString()

    @TypeConverter
    fun uuidFromStorage(value: String?): Uuid? = value?.let(Uuid::parse)

    @TypeConverter
    fun instantToStorage(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun instantFromStorage(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToStorage(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun localDateFromStorage(value: String?): LocalDate? = value?.let(LocalDate::parse)

    /** Минуты от начала суток: время расписания сравнивается и сортируется числом. */
    @TypeConverter
    fun localTimeToStorage(value: LocalTime?): Int? = value?.toSecondOfDay()?.div(SECONDS_IN_MINUTE)

    @TypeConverter
    fun localTimeFromStorage(value: Int?): LocalTime? =
        value?.let { LocalTime.ofSecondOfDay(it.toLong() * SECONDS_IN_MINUTE) }

    @TypeConverter
    fun zoneIdToStorage(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun zoneIdFromStorage(value: String?): ZoneId? = value?.let(ZoneId::of)

    /** Битовая маска: понедельник — младший бит, потому что таков порядок `DayOfWeek.value`. */
    @TypeConverter
    fun daysOfWeekToStorage(value: Set<DayOfWeek>?): Int? =
        value?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    @TypeConverter
    fun daysOfWeekFromStorage(value: Int?): Set<DayOfWeek>? = value?.let { mask ->
        DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1 shl (it.value - 1)) != 0 }
    }

    private const val SECONDS_IN_MINUTE = 60
}
