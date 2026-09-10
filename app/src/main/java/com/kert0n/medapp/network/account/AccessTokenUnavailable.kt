package com.kert0n.medapp.network.account

import java.io.IOException
import kotlin.time.Duration

/**
 * Пропуск сейчас не получить: сеть, отказ сервера или лимит выдачи. Это не «учётка не принята» —
 * учётка может быть в порядке, — и запрос, ради которого он понадобился, на сервер ещё не ушёл,
 * значит повторить его позже безопасно.
 *
 * `IOException`, потому что иначе ей не пройти сквозь хук авторизации до того, кто объявлял
 * операцию: он и превращает её в исход, а не в исключение.
 */
open class AccessTokenUnavailable(message: String) : IOException(message)

/**
 * Лимит выдачи пропусков по адресу исчерпан. Отдельный случай, потому что ждут по нему иначе:
 * срок называет сервер, и подменять его своим backoff нельзя (PLAN B5).
 */
class AccessTokenThrottled(val retryAfter: Duration?) :
    AccessTokenUnavailable("пропуск не выдан: лимит выдачи по адресу")
