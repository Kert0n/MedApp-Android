package com.kert0n.medapp.network.account

import java.io.IOException

/**
 * Пропуск сейчас не получить: сеть, отказ сервера или лимит выдачи. Это не «учётка не принята» —
 * учётка может быть в порядке, — и запрос, ради которого он понадобился, на сервер ещё не ушёл,
 * значит повторить его позже безопасно.
 */
class AccessTokenUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)
