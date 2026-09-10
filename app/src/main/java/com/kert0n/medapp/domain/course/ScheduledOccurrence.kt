package com.kert0n.medapp.domain.course

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Пункт расписания: назначенные дата и время — они, до разрешения перехода часов, и есть его
 * тождество (PLAN F4) — и момент [at], когда они наступают. Внутри пропущенного часа момент может
 * совпасть у двух пунктов, и пунктов при этом остаётся два.
 */
data class ScheduledOccurrence(
    val localDate: LocalDate,
    val localTime: LocalTime,
    val at: Instant
)
