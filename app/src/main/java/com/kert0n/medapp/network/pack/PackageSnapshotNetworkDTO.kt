package com.kert0n.medapp.network.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Пачка вместе с картиной броней на ней (`DrugSnapshotDTO`) — так сервер отвечает на чтение и
 * на любую команду над пачкой. У двух половин свои версии, и вместе они приходят только здесь.
 */
@Serializable
data class PackageSnapshotNetworkDTO(
    @SerialName("drug") val pack: PackageNetworkDTO,
    @SerialName("reservations") val claims: ClaimsNetworkDTO
)
