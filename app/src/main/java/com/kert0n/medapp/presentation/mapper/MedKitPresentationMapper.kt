package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.MedKit
import com.kert0n.medapp.presentation.dto.MedKitPresentationDTO
import java.time.Instant

/**
 * Преобразует состояние до операторов потока, которые сравнивают значения через equals.
 *
 * [syncedAt] приходит аргументом: момент последней сверки принадлежит обвязке синхронизации,
 * а не аптечке.
 */
fun MedKit.toPresentationDTO(syncedAt: Instant? = null): MedKitPresentationDTO =
    MedKitPresentationDTO(
        id = id,
        name = name,
        location = location,
        publication = publication,
        participantCount = participantCount,
        createdAt = createdAt,
        syncedAt = syncedAt,
        isShared = isShared,
        acceptsInvitations = acceptsInvitations
    )
