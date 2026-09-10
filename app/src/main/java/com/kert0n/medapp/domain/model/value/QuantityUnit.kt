package com.kert0n.medapp.domain.model.value

import kotlin.uuid.Uuid

const val VOCABULARY_NAME_MAX_LENGTH = 200

/**
 * Единица измерения из общего словаря. Идентификаторы **серверные**: словарь один на всю
 * систему, и клиент хранит его снимок (PLAN F1).
 */
data class QuantityUnit(val id: Uuid, val name: String) {
    init {
        requireText(name, VOCABULARY_NAME_MAX_LENGTH, "QuantityUnit.name")
    }
}

