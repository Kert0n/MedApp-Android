package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Ошибка в формате `application/problem+json` (RFC 7807) — так отвечает сервер на всё, что не
 * получилось, включая отказы уровня безопасности.
 *
 * В опубликованном контракте схемы у ошибок нет: там объявлены только коды. Этот класс списан с
 * того, что сервер действительно пишет, — `ProblemDetail` Spring без дополнительных полей.
 *
 * Формулировки грубые намеренно: вызывающий узнаёт класс проблемы, но никогда — какая запись и
 * сколько её. Недоступное неотличимо от несуществующего, иначе по коду ответа перебором
 * узнавалось бы чужое.
 */
@Serializable
data class ProblemDTO(
    /** `about:blank`, пока сервер не заведёт собственных типов ошибок. */
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null
)
