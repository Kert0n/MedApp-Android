package com.kert0n.medapp.domain.value

import kotlin.uuid.Uuid

/**
 * Единица измерения из общего словаря. Идентификаторы **серверные**: словарь один на всю
 * систему, и клиент хранит его снимок (PLAN F1).
 *
 * Сущность, а не величина: переименованная на сервере единица — та же единица, и количества,
 * записанные в ней вчера, складываются с сегодняшними. Тождество — [id]; имя — сведения.
 */
class QuantityUnit(val id: Uuid, val name: String) {
    init {
        requireText(name, NAME_MAX_LENGTH, "QuantityUnit.name")
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is QuantityUnit && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "QuantityUnit(id=$id, name=$name)"

    companion object {
        const val NAME_MAX_LENGTH = 200
    }
}
