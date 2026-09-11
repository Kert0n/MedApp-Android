package com.kert0n.medapp.queue

/**
 * Чем кончилась подготовка команды по свежему состоянию пачки — «подумали» перед отправкой
 * (PLAN E2, E3). Случая три, и очередь делает с ними разное: запрос уходит; отказ закрывает
 * операцию, не тревожа сервер, — состояние, к которому она обращена, для неё негодно; «уже
 * так» закрывает её применённой — на сервере и без нас то, чего команда хотела.
 */
sealed interface Preparation {

    data class Request(val request: PreparedRequest) : Preparation

    data class Refuse(val reason: RefusalReason) : Preparation

    data object AlreadyApplied : Preparation
}
