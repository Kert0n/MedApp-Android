package com.kert0n.medapp.network.pack

import com.kert0n.medapp.network.server.ResourceVersion
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Подтверждённое сервером состояние пачки (`DrugDTO`): серверная половина сведений, остаток и
 * версия её состояния — предусловие следующей команды (PLAN B3). Личных сведений здесь нет по
 * контракту, поэтому снимок и не может их стереть.
 */
@Serializable
data class PackageNetworkDTO(
    val id: Uuid,
    val name: String,
    @SerialName("quantity") val amount: String,
    @SerialName("quantityUnitId") val unitId: Uuid,
    @SerialName("formTypeId") val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val medKitId: Uuid,
    val version: ResourceVersion
) {
    init {
        requireNetworkAmount(amount, "PackageNetworkDTO.amount")
    }
}
