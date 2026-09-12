package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Черновик глазами экрана: что уже названо и какие пачки выбраны. Величина без переходов
 * (PLAN H1); строит её черновик ([CourseDraft.projection]).
 */
data class CourseDraftProjection(
    val id: Uuid,
    val title: String,
    val note: String?,
    val dose: Dose?,
    val form: DosageForm?,
    val schedule: CourseSchedule?,
    val totalDoses: Doses?,
    val sources: List<CourseSource>,
    val revision: Revision,
    val createdAt: Instant,
    val updatedAt: Instant
)
