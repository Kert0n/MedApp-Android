package com.kert0n.medapp.domain.medkit

import kotlin.uuid.Uuid

/**
 * Ссылка на аптечку из чужого агрегата — то, что пачке и движению нужно о ней знать: тождество
 * и есть ли она на сервере. Переходов у ссылки нет: переименовать или опубликовать аптечку через
 * устаревшую копию, лежащую внутри пачки, нечем — это делает сама [MedKit] в своей транзакции.
 *
 * Равенство по [id]: ссылка указывает на вещь, и та же аптечка, прочитанная позже, — та же
 * ссылка.
 */
class MedKitRef(
    val id: Uuid,
    val publication: MedKit.Publication
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is MedKitRef && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "MedKitRef(id=$id, publication=$publication)"
}
