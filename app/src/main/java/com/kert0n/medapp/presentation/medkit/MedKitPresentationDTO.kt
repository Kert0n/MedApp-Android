package com.kert0n.medapp.presentation.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import java.time.Instant
import kotlin.uuid.Uuid

/** Состояние аптечки сравнивается по всем полям, а не только по её тождеству. */
data class MedKitPresentationDTO(
    val id: Uuid,
    val name: String,
    val location: String?,
    val publication: MedKit.Publication,
    val participantCount: Long,
    val createdAt: Instant,
    val syncedAt: Instant?,
    val isShared: Boolean,
    val acceptsInvitations: Boolean,
    val packages: Int,
    val expired: Int
) {
    /** Просрочку человек видит первой, поэтому у неё свой вопрос, а не сравнение чисел на экране. */
    val hasExpired: Boolean get() = expired > 0

    val isEmpty: Boolean get() = packages == 0
}
