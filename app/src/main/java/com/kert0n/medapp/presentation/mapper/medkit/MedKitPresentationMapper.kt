package com.kert0n.medapp.presentation.mapper.medkit

import com.kert0n.medapp.domain.model.medkit.MedKit
import com.kert0n.medapp.presentation.dto.medkit.MedKitPresentationDTO
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
