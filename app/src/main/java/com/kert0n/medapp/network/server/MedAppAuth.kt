package com.kert0n.medapp.network.server

import com.kert0n.medapp.network.account.AccessTokenNetworkDTO
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
 * Выдача, которая не удалась по сети или отказу сервера, — [AccessTokenUnavailable], а не 401:
 * учётка может быть в порядке, а запрос ещё не ушёл.
 */
val MedAppAuth = createClientPlugin("MedAppAuth", ::MedAppAuthConfig) {
    val tokens = pluginConfig.tokens
    val http = client

    suspend fun issue(account: AccountCredentials): String? {
        val response = try {
            http.post(TOKEN_PATH) { basicAuth(account.login.toString(), account.key) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw AccessTokenUnavailable("пропуск не выдан: нет связи", cause)
        }
        return when (response.status) {
            HttpStatusCode.OK -> try {
                response.body<AccessTokenNetworkDTO>().accessToken
            } catch (cause: Exception) {
                throw AccessTokenUnavailable("пропуск не выдан: ответ не по контракту", cause)
            }
            HttpStatusCode.Unauthorized -> null
            else -> throw AccessTokenUnavailable("пропуск не выдан: ${response.status.value}")
        }
    }

    on(Send) { request ->
        if (request.url.encodedPath.startsWith(AUTH_PATH)) return@on proceed(request)

        val token = tokens.current ?: tokens.renew(null) { issue(it) }
            ?: return@on proceed(request)
        request.headers[HttpHeaders.Authorization] = "Bearer $token"
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call

        val renewed = tokens.renew(token) { issue(it) } ?: return@on call
        request.headers[HttpHeaders.Authorization] = "Bearer $renewed"
        proceed(request)
    }
}

private const val AUTH_PATH = "/v1/auth/"
private const val TOKEN_PATH = "/v1/auth/token"
