package com.kert0n.medapp.network.account

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Учётка, выданная регистрацией (`RegisterResponse`). `key` показывается сервером **один раз**:
 * потерять его — потерять учётку, поэтому он не попадает ни в логи, ни в `toString` (PLAN G2).
 */
@Serializable
data class AccountRegisteredNetworkDTO(
    val login: Uuid,
    val key: String
) {
    override fun toString(): String = "AccountRegisteredNetworkDTO(login=$login, key=***)"
}
