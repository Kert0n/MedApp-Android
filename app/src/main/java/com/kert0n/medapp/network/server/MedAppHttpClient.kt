package com.kert0n.medapp.network.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Клиент сервера MedApp (PLAN H2): строгий формат провода, таймауты и автоповтор **только
 * чтений**. Изменяющую команду HTTP-слой не повторяет: её исход мог примениться, и что делать
 * дальше, решает политика операции (PLAN E3), а не транспорт.
 *
 * Статус ответа исключением не становится: успех у каждой операции свой, и проверяет его тот,
 * кто операцию объявил.
 */
fun medAppHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    retryDelay: HttpRequestRetryConfig.() -> Unit = { exponentialDelay(randomizationMs = 500) }
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(medAppJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 30_000
    }
    install(HttpRequestRetry) {
        retryIf(READ_RETRIES) { request, response ->
            request.method == HttpMethod.Get && response.status.value >= 500
        }
        retryOnExceptionIf(READ_RETRIES) { request, cause ->
            request.method == HttpMethod.Get && cause !is CancellationException
        }
        retryDelay()
    }
    defaultRequest { url(baseUrl) }
}

private const val READ_RETRIES = 3
