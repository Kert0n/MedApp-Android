package com.kert0n.medapp.data.sync.pack

import com.kert0n.medapp.domain.model.course.CourseBrief
import com.kert0n.medapp.domain.model.pack.ClaimOwnership
import com.kert0n.medapp.domain.model.sync.ReleaseClaimIntent
import com.kert0n.medapp.domain.model.sync.SyncIntent

/**
 * Отвечает домену, чем объяснена моя бронь на пачку: назначением, локальным снятием — или ничем.
 *
 * Ожидающая команда очереди — **один из источников** этого смысла, поэтому она и остаётся здесь,
 * а не уезжает в домен булевым флагом: назначение курса приходит из базы, снятие может быть уже
 * отправлено, а бронь без того и другого — та самая бесхозная, которую разбирает человек.
 *
 * Ожидающая команда брони без локального назначения намеренно даёт [ClaimOwnership.NoKnownOwner]:
 * бронь, за которой не стоит ни один курс, — это и есть неизвестное назначение, и прятать его
 * фактом «что-то отправляется» значило бы не показать человеку то, что он должен разобрать.
 */
fun claimOwnership(
    assignedCourse: CourseBrief?,
    unclosed: List<SyncIntent> = emptyList()
): ClaimOwnership = when {
    unclosed.any { it is ReleaseClaimIntent } -> ClaimOwnership.ReleasedLocally
    assignedCourse != null -> ClaimOwnership.AssignedTo(assignedCourse)
    else -> ClaimOwnership.NoKnownOwner
}
