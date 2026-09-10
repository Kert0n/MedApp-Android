package com.kert0n.medapp.network.account

import com.kert0n.medapp.network.medkit.MedKitNetworkDTO
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Всё, что видит учётка, одним ответом (`UserSnapshotDTO`). Дельты нет, поэтому каждая
 * синхронизация читает именно его (PLAN B6).
 */
@Serializable
data class AccountSnapshotNetworkDTO(
    val id: Uuid,
    val medKits: List<MedKitNetworkDTO>
)
