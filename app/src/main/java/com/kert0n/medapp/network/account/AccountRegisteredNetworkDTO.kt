package com.kert0n.medapp.network.account

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Учётка, выданная регистрацией (`RegisterResponse`). `key` показывается сервером **один раз**:
 * потерять его — потерять учётку, поэтому он не попадает ни в логи, ни в `toString` (PLAN G2).
 *
 * Пустой ключ отвергается здесь, на границе разбора: дальше он стал бы `AccountCredentials`, и
 * то же правило сработало бы уже исключением мимо объявленного исхода операции.
 */
@Serializable
data class AccountRegisteredNetworkDTO(
    val login: Uuid,
    val key: String
) {
    init {
        require(key.isNotEmpty()) { "выданная учётка называет свой ключ" }
    }

    override fun toString(): String = "AccountRegisteredNetworkDTO(login=$login, key=***)"
}
