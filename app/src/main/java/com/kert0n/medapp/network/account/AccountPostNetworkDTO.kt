package com.kert0n.medapp.network.account

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Учётные данные, которые устройство просит сервер запомнить (`RegisterRequest`). Предел пароля —
 * правило регистрации, и проверяется он здесь, на границе: 32–72 печатных знака ASCII без пробела,
 * где 72 байта — предел bcrypt на сервере (PLAN B1, B4). В логи и `toString` пароль не попадает.
 */
@Serializable
data class AccountPostNetworkDTO(
    val login: Uuid,
    val password: String
) {
    init {
        require(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "пароль регистрации — от $MIN_PASSWORD_LENGTH до $MAX_PASSWORD_LENGTH знаков"
        }
        require(password.all { it in PRINTABLE }) {
            "пароль регистрации записывается печатными знаками ASCII без пробела"
        }
    }

    override fun toString(): String = "AccountPostNetworkDTO(login=$login, password=***)"

    companion object {

        const val MIN_PASSWORD_LENGTH = 32

        const val MAX_PASSWORD_LENGTH = 72

        private val PRINTABLE = '!'..'~'
    }
}
