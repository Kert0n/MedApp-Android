package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.MedKit
import com.kert0n.medapp.presentation.dto.MedKitPresentationDTO

/** Преобразует состояние до операторов потока, которые сравнивают значения через equals. */
fun MedKit.toPresentationDTO(): MedKitPresentationDTO = MedKitPresentationDTO(
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
