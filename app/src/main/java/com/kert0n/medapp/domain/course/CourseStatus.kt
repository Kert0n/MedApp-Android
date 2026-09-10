package com.kert0n.medapp.domain.course

/**
 * Состояние курса. [DRAFT] — законное сохраняемое состояние без расписания, дозы или пачек;
 * [COMPLETED] и [CANCELLED] историю не переписывают (PLAN D5).
 */
enum class CourseStatus {
    DRAFT,        // ещё нет расписания или источников
    ACTIVE,
    COMPLETED,    // календарь закончился, неотвеченных пунктов нет
    CANCELLED
}
