package com.kert0n.medapp.domain.course

import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Запись эпизода глазами экрана и аналитики: идущее и законченное лечение одной формы (PLAN H6).
 * Величина без переходов; строит её запись ([CourseRecord.projection]).
 */
data class CourseRecordProjection(
    val id: Uuid,
    val title: String,
    val note: String?,
    val prescription: Prescription,
    val startedAt: Instant,
    val outcome: CourseRecord.Outcome?,
    val closedAt: Instant?
) {
    val isOpen: Boolean get() = outcome == null
}
