package com.kert0n.medapp.network.server

import kotlinx.serialization.json.Json

/**
 * Формат провода MedApp (PLAN H2). Неизвестное поле — ошибка, а не молча потерянный смысл:
 * расхождение с контрактом падает в тесте. Значения по умолчанию и `null` не отправляются,
 * потому что у PATCH отсутствие и `null` значат «не трогать», а очистка выражается `""`.
 */
val medAppJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = false
    explicitNulls = false
}
