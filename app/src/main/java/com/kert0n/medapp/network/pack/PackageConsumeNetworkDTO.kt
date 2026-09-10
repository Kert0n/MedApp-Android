package com.kert0n.medapp.network.pack

import com.kert0n.medapp.network.server.ResourceVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Расход из пачки (`IntakeRequest`). Повторять его можно только с **исходной** версией: свежая
 * списала бы неустановленный расход второй раз (PLAN B4, E3).
 */
@Serializable
data class PackageConsumeNetworkDTO(
    @SerialName("quantity") val amount: String,
    val version: ResourceVersion? = null
) {
    init {
        requirePositiveNetworkAmount(amount, "PackageConsumeNetworkDTO.amount")
    }
}
