package com.kert0n.medapp.network.account

import kotlin.uuid.Uuid

/**
 * Учётка устройства: `login` и `key`, выданные регистрацией один раз (PLAN B1, G2). По ней
 * добывается короткий пропуск; ключ секретен и в `toString` не показывается.
 */
data class AccountCredentials(val login: Uuid, val key: String) {
    init {
        require(key.isNotEmpty()) { "учётка без ключа ничего не открывает" }
    }

    override fun toString(): String = "AccountCredentials(login=$login, key=***)"
}
