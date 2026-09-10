package com.kert0n.medapp.network.value

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Запись общего словаря единиц или форм (`VocabularyEntryDTO`). Идентификатор серверный:
 * его придумывает не устройство, и пачки ссылаются именно на него.
 */
@Serializable
data class VocabularyEntryNetworkDTO(
    val id: Uuid,
    val name: String
)
