package com.kert0n.medapp.network.medkit

import kotlinx.serialization.Serializable

/**
 * Приглашение в аптечку (`InvitationDTO`). Ключ — секрет: он открывает чужую аптечку и потому не
 * попадает ни в логи, ни в сообщения об ошибках, в том числе через `toString` (PLAN G3).
 */
@Serializable
data class InvitationNetworkDTO(val key: String) {
    override fun toString(): String = "InvitationNetworkDTO(key=***)"
}
