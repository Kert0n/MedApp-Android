package com.kert0n.medapp.network.server

import com.kert0n.medapp.network.account.AccessTokenIssue
import com.kert0n.medapp.network.account.AccessTokenNetworkDTO
import com.kert0n.medapp.network.account.AccessTokenThrottled
import com.kert0n.medapp.network.account.AccessTokenUnavailable
import com.kert0n.medapp.network.account.AccessTokens
import com.kert0n.medapp.network.account.AccountCredentials
import io.ktor.client.call.body
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.basicAuth
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlin.coroutines.cancellation.CancellationException

class MedAppAuthConfig {
    lateinit var tokens: AccessTokens
}

/**
 * Авторизация MedApp (PLAN B1, B5): пропуск в заголовке каждого запроса, кроме регистрации и
 * выдачи пропуска, и **один** перевыпуск на 401 с одним повтором. Повтор безопасен и для изменяющей команды:
 * 401 значит, что сервер её не принял к исполнению. Второй 401 возвращается как есть — по кругу
 * пропуск не просят.
 *
 * Выдача отвечает исходом, а не строкой с исключением наперевес: отказ учётки, лимит выдачи и
 * недоступность — разные решения вызывающего, и различает их [AccessTokenIssue], а не то, чем
 * кончился разбор ответа.
 */
val MedAppAuth = createClientPlugin("MedAppAuth", ::MedAppAuthConfig) {
    val tokens = pluginConfig.tokens
    val http = client

    suspend fun issue(account: AccountCredentials): AccessTokenIssue {
        val response = try {
            http.post(TOKEN_PATH) { basicAuth(account.login.toString(), account.key) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return AccessTokenIssue.Unavailable("нет связи")
        }
        return when (response.status) {
            HttpStatusCode.OK -> try {
                AccessTokenIssue.Issued(response.body<AccessTokenNetworkDTO>().accessToken)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                AccessTokenIssue.Unavailable("ответ не по контракту")
            }
            HttpStatusCode.Unauthorized -> AccessTokenIssue.Rejected
            HttpStatusCode.TooManyRequests -> AccessTokenIssue.Throttled(response.retryAfter())
            else -> AccessTokenIssue.Unavailable("отказ ${response.status.value}")
        }
    }

    /** `null` — пропуска нет и взять его не по чему; запрос идёт без заголовка и получит 401. */
    suspend fun token(stale: String?): String? =
        when (val outcome = tokens.renew(stale) { issue(it) }) {
            is AccessTokenIssue.Issued -> outcome.token
            AccessTokenIssue.Rejected -> null
            is AccessTokenIssue.Throttled -> throw AccessTokenThrottled(outcome.retryAfter)
            is AccessTokenIssue.Unavailable ->
                throw AccessTokenUnavailable("пропуск не выдан: ${outcome.reason}")
        }

    on(Send) { request ->
        if (request.url.encodedPath.startsWith(AUTH_PATH)) return@on proceed(request)

        val token = tokens.current ?: token(null) ?: return@on proceed(request)
        request.headers[HttpHeaders.Authorization] = "Bearer $token"
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call

        val renewed = token(token) ?: return@on call
        request.headers[HttpHeaders.Authorization] = "Bearer $renewed"
        proceed(request)
    }
}

private const val AUTH_PATH = "/v1/auth/"
private const val TOKEN_PATH = "/v1/auth/token"
