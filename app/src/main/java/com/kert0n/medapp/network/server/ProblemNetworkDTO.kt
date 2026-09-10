package com.kert0n.medapp.network.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Тело отказа сервера (`application/problem+json`, PLAN B5). Код ответа и есть код ошибки: `type`
 * всегда `about:blank`, а `detail` описывает нарушенное ограничение по-английски и человеку не
 * показывается. Смысл несёт только `errors[]` при 400.
 *
 * Поэтому разбирается оно нестрого ([problemJson]): это диагностика, и сломанное тело отказа не
 * должно отнимать решение, которое уже даёт статус.
 */
@Serializable
data class ProblemNetworkDTO(
    val status: Int? = null,
    val detail: String? = null,
    val errors: List<FieldError> = emptyList()
) {
    /** Поле запроса и что с ним не так. */
    @Serializable
    data class FieldError(val field: String, val reason: String)
}

internal val problemJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
