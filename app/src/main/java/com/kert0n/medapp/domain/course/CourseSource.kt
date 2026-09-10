package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import kotlin.uuid.Uuid

/**
 * Пачка препарата курса и сколько целых доз из неё ещё выделено — оставшееся выделение, а не
 * первоначальное: подтверждённая доза уменьшает его на одну. Величина: тождество даёт пара «курс и
 * пачка», а место в препарате — очередь расходования (PLAN D5).
 */
data class CourseSource(
    val packageId: Uuid,
    val allocatedDoses: Doses
)
