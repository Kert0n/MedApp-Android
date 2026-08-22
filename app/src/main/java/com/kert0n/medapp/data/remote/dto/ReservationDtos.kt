package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Бронь: сколько из этой упаковки человек считает своим.
 *
 * Без отметок времени: когда человек трогал свою аптечку — не дело сервера.
 */
@Serializable
data class ReservationDTO(
    val drugId: String,
    /** Может превышать остаток пачки: сколько из своей брони оставить, решает её владелец. */
    val amount: String
)

/** Завести бронь на часть упаковки. */
@Serializable
data class ReservationCreateRequest(
    val drugId: String,
    /** Строго больше нуля. */
    val amount: String,
    val version: Long? = null
)

/**
 * Изменить заявленное количество.
 *
 * Ноль здесь не означает отмену: брони с нулём не бывает, а отмена — это `DELETE`.
 */
@Serializable
data class ReservationPatchRequest(
    val amount: String,
    val version: Long? = null
)

/** Приём: съеденное уменьшает упаковку, а бронь её владелец правит отдельно. */
@Serializable
data class IntakeRequest(
    val quantity: String,
    val version: Long? = null
)
