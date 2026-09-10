package com.kert0n.medapp.network.account

import kotlin.time.Duration

/**
 * Чем кончилась попытка получить пропуск по учётке. Случаи различает поведение вызывающего
 * (PLAN B1, B5): по [Issued] работают; по [Rejected] пропуск по тому же ключу больше не просят —
 * учётка не принята, и второй раз ответ будет тем же; [Throttled] и [Unavailable] значат, что
 * запрос ещё не ушёл и повторить его позже безопасно, но ждут по-разному — срок называет сервер
 * либо вызывающий берёт свой ограниченный backoff.
 */
sealed interface AccessTokenIssue {

    data class Issued(val token: String) : AccessTokenIssue

    /** Пропуска нет и взять его не по чему: учётки нет, она нечитаема или сервер её не принял. */
    data object Rejected : AccessTokenIssue

    /** Лимит выдачи по адресу исчерпан. `null` в [retryAfter] — срок сервер не назвал. */
    data class Throttled(val retryAfter: Duration? = null) : AccessTokenIssue

    /** Связи нет, сервер недоступен или ответил не по контракту; [reason] — для журнала. */
    data class Unavailable(val reason: String) : AccessTokenIssue
}
