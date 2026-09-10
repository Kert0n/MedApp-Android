package com.kert0n.medapp.network.medkit

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Сводка аптечки (`MedKitSummaryDTO`): участники и состав без количеств, версий и броней.
 * Дешёвой сверкой она не служит — чужой приём состава не меняет (PLAN B6).
 */
@Serializable
data class MedKitSummaryNetworkDTO(
    val id: Uuid,
    @SerialName("userCount") val participantCount: Long,
    @SerialName("drugIds") val packageIds: List<Uuid>
)
