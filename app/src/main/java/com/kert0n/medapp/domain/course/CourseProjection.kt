package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Живой план глазами экрана: назначение, источники по порядку расходования, дозы мимо плана,
 * редакция. Величина без переходов (PLAN H1); строит её курс ([Course.projection]).
 */
data class CourseProjection(
    val id: Uuid,
    val prescription: Prescription,
    val sources: List<CourseSource>,
    val takenOffPlan: Doses,
    val revision: Revision,
    val createdAt: Instant,
    val updatedAt: Instant
)
