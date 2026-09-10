package com.kert0n.medapp.network.medkit

import kotlinx.serialization.Serializable

/**
 * Вступление в аптечку по ключу приглашения (`MembershipCreateRequest`). Ключ — секрет и в
 * `toString` не показывается (PLAN G3).
 */
@Serializable
data class MembershipPostNetworkDTO(val key: String) {
    init {
        require(key.isNotEmpty()) { "вступление называет ключ приглашения" }
    }

    override fun toString(): String = "MembershipPostNetworkDTO(key=***)"
}
