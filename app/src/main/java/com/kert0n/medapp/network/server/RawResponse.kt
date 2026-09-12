package com.kert0n.medapp.network.server

/**
 * Успешный ответ на готовый запрос, как его прислал сервер: статус и тело строкой. Что они
 * значат — снимок, бронь, ничего, — решает тот, кто запрос готовил; сеть только доставляет.
 */
data class RawResponse(val status: Int, val body: String)
