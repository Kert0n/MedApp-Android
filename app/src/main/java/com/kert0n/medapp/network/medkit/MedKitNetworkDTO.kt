package com.kert0n.medapp.network.medkit

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Аптечка с содержимым (`MedKitDTO`): число участников и все пачки с бронями. Версии у аптечки
 * нет — участие самостоятельная строка, а не элемент версионируемого списка (PLAN B3).
 */
@Serializable
data class MedKitNetworkDTO(
    val id: Uuid,
    @SerialName("userCount") val participantCount: Long,
    @SerialName("drugs") val packages: List<PackageSnapshotNetworkDTO>
)
