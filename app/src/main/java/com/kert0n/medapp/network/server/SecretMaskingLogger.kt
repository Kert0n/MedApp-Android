package com.kert0n.medapp.network.server

import io.ktor.client.plugins.logging.Logger

/**
 * Лог HTTP без секретов (PLAN G2, G3). Заголовки прячет сам плагин, а тела — этот логгер: ключ
 * регистрации приходит именно в теле ответа, как и пропуск, и ключ приглашения, поэтому
 * маскировать один заголовок недостаточно. Маскируется значение, а не строка целиком — рядом
 * лежит то, что нужно для отладки.
 */
class SecretMaskingLogger(private val delegate: Logger) : Logger {

    override fun log(message: String) {
        delegate.log(mask(message))
    }

    internal fun mask(message: String): String = message
        .replace(SECRET_FIELD) { "\"${it.groupValues[1]}\":\"$MASK\"" }
        .replace(CREDENTIAL_SCHEME) { "${it.groupValues[1]} $MASK" }

    private companion object {
        const val MASK = "***"
        val SECRET_FIELD = Regex(""""(key|accessToken)"\s*:\s*"[^"]*"""")
        val CREDENTIAL_SCHEME = Regex("""\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]+""")
    }
}
