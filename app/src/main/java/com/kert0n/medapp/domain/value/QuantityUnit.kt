package com.kert0n.medapp.domain.value

import kotlin.uuid.Uuid

/**
 * Единица измерения из общего словаря. Идентификаторы **серверные**: словарь один на всю
 * систему, и клиент хранит его снимок (PLAN F1).
 */
data class QuantityUnit(val id: Uuid, val name: String) {
    init {
        requireText(name, NAME_MAX_LENGTH, "QuantityUnit.name")
    }

    companion object {
        const val NAME_MAX_LENGTH = 200
    }
}
