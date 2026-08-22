package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Синхронизация одной упаковки: съеденное и новая бронь одним запросом.
 *
 * Двумя запросами нельзя — между списанием и уменьшением брони осталось бы окно, в котором
 * срабатывают уведомления «лекарства мало». Здесь обе части применяются одной транзакцией.
 *
 * Версии едут в теле, а не параметром: запрос меняет два состояния сразу. Отсюда и код ответа —
 * несовпадение версии из тела даёт 409, а не 412.
 */
@Serializable
data class DrugSyncRequest(
    /** Дельта: сколько съедено за офлайн. Отсутствует, когда не принимали. */
    val consumed: String? = null,
    val drugVersion: Long? = null,
    /** Отсутствует, когда бронь не менялась. */
    val reservation: ReservationSyncRequest? = null
)

/** Бронь после офлайна — абсолютным значением: это решение владельца, а не накопленное событие. */
@Serializable
data class ReservationSyncRequest(
    val amount: String,
    /** Отсутствует, когда брони ещё нет: сверяться не с чем, её заводят. */
    val version: Long? = null
)
