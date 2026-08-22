package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/** Снимок вызывающего: всё, что ему видно, одним ответом. Им клиент и синхронизируется целиком. */
@Serializable
data class UserSnapshotDTO(
    val id: String,
    val medKits: Set<MedKitDTO>
)

/** Выданный токен доступа. Срок жизни — в claim `exp`, отдельным полем его не дублируют. */
@Serializable
data class TokenResponse(
    val accessToken: String
)

/**
 * Учётные данные, сгенерированные при регистрации.
 *
 * Другого способа их узнать нет: о человеке не хранится ничего, кроме идентификатора и хеша
 * ключа, — восстановить ключ сервер не может.
 */
@Serializable
data class RegisterResponse(
    val login: String,
    val key: String
)
