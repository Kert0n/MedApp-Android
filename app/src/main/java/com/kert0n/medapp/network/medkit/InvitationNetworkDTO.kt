package com.kert0n.medapp.network.medkit

import kotlinx.serialization.Serializable

/**
 * Приглашение в аптечку (`InvitationDTO`). Ключ — секрет: он открывает чужую аптечку и потому не
 * попадает ни в логи, ни в сообщения об ошибках, в том числе через `toString` (PLAN G3).
 *
 * Пустой ключ отвергается на границе разбора: показывать человеку приглашение, которое ничего
 * не открывает, хуже, чем назвать ответ сервера не соответствующим контракту.
 */
@Serializable
data class InvitationNetworkDTO(val key: String) {
    init {
        require(key.isNotEmpty()) { "приглашение называет ключ, по которому в аптечку входят" }
    }

    override fun toString(): String = "InvitationNetworkDTO(key=***)"
}
