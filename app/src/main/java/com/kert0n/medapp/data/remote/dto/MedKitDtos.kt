package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/** Аптечка вместе с содержимым. Участники равноправны: ни владельца, ни ролей — только число. */
@Serializable
data class MedKitDTO(
    val id: String,
    val userCount: Long,
    val drugs: Set<DrugSnapshotDTO>
)

/**
 * Счётчики аптечки без её содержимого.
 *
 * Отвечает на вопрос «что ещё на месте», а не «что внутри»: состояние пачек клиент берёт полным
 * обновлением.
 */
@Serializable
data class MedKitSummaryDTO(
    val id: String,
    val userCount: Long,
    val drugIds: Set<String>
)

/** Ответ на заведение аптечки. */
@Serializable
data class MedKitCreatedDTO(
    val id: String
)

/** Ключ приглашения: живёт ограниченное время и внутри него используется повторно. */
@Serializable
data class InvitationDTO(
    val key: String
)

/** Вступление в аптечку по ключу приглашения. */
@Serializable
data class MembershipCreateRequest(
    val key: String
)
