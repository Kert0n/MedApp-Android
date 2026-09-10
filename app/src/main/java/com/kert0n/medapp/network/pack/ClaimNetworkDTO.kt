package com.kert0n.medapp.network.pack

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Моя бронь на пачке (`ReservationDTO`). Версии картины броней в ответе нет (PLAN B6): её
 * узнают следующим чтением пачки.
 */
@Serializable
data class ClaimNetworkDTO(
    @SerialName("drugId") val packageId: Uuid,
    val amount: String
) {
    init {
        requirePositiveNetworkAmount(amount, "ClaimNetworkDTO.amount")
    }
}
