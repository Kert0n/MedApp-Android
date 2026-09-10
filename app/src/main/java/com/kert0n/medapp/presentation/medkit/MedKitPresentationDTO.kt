package com.kert0n.medapp.presentation.medkit

import com.kert0n.medapp.domain.medkit.KitPublication
import java.time.Instant
import kotlin.uuid.Uuid

/** Состояние аптечки сравнивается по всем полям, а не только по её тождеству. */
data class MedKitPresentationDTO(
    val id: Uuid,
    val name: String,
    val location: String?,
    val publication: KitPublication,
    val participantCount: Long,
    val createdAt: Instant,
    val syncedAt: Instant?,
    val isShared: Boolean,
    val acceptsInvitations: Boolean
)
