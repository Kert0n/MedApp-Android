package com.kert0n.medapp.network.server

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `Retry-After` в секундах; дату и прочее вызывающий заменяет своим backoff (PLAN B5). Заголовок
 * один и там, где 429 отдаёт ресурс, и там, где его отдаёт выдача пропуска, поэтому читается он
 * тоже в одном месте.
 */
internal fun HttpResponse.retryAfter(): Duration? =
    headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds
