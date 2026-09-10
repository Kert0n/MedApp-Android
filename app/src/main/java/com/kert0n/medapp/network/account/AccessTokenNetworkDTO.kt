package com.kert0n.medapp.network.account

import kotlinx.serialization.Serializable

/**
 * Короткий пропуск (`TokenResponse`), выдаваемый по учётке. Живёт только в памяти процесса и в
 * `toString` не показывается (PLAN G2).
 */
@Serializable
data class AccessTokenNetworkDTO(val accessToken: String) {
    override fun toString(): String = "AccessTokenNetworkDTO(accessToken=***)"
}
