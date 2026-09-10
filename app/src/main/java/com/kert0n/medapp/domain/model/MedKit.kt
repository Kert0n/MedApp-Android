package com.kert0n.medapp.domain.model

import java.time.Instant
import kotlin.uuid.Uuid

const val MED_KIT_NAME_MAX_LENGTH = 200

const val MED_KIT_LOCATION_MAX_LENGTH = 300

enum class KitPublication {
    LOCAL,        // на сервере не существует
    PUBLISHING,   // группа операций публикации ещё не завершена
    PUBLISHED     // существует на сервере
}

/**
 * Аптечка — место хранения и контейнер упаковок.
 *
 * Сервер знает о ней только существование, участие и число участников. Название и место
 * хранения не уезжают никуда и не передаются обходными «конвертами» в описании препарата
 * (PLAN C0, E5).
 *
 * **Это сущность, а не величина.** Переименованная аптечка — та же аптечка, и в ней лежат те же
 * пачки. Тождество — [id], равенство идёт по нему; `data class` утверждал бы обратное, что смена
 * названия даёт другую аптечку.
 *
 * Конструктор публичный и проверяет то, что верно про аптечку всегда. Момент последней сверки с
 * сервером сюда не входит: он нужен хранению и экрану состояния синхронизации, а не правилам.
 */
class MedKit(
    val id: Uuid,                   // придуман клиентом; он же серверный
    val name: String,               // 1..200, только на устройстве
    val location: String?,          // ≤300, место хранения; только на устройстве
    val publication: KitPublication,
    val participantCount: Long,     // 1 у локальной, иначе userCount с сервера
    val createdAt: Instant
) {

    init {
        requireText(name, MED_KIT_NAME_MAX_LENGTH, "MedKit.name")
        requireOptionalText(location, MED_KIT_LOCATION_MAX_LENGTH, "MedKit.location")
        require(participantCount >= 1) { "участник всегда есть хотя бы один — я сам" }
        if (publication == KitPublication.LOCAL) {
            require(participantCount == 1L) { "у локальной аптечки других участников нет" }
        }
    }

    val isShared: Boolean get() = participantCount > 1

    /**
     * Отдельно от [isShared]. Пока группа операций публикации не завершена целиком, часть пачек
     * на сервере уже есть, а часть нет: приглашённый увидел бы половину аптечки (PLAN D2).
     */
    val acceptsInvitations: Boolean get() = publication == KitPublication.PUBLISHED

    /** Меняет личные сведения, сохраняя тождество и состояние публикации аптечки. */
    fun describe(name: String, location: String?): MedKit = MedKit(
        id = id,
        name = name,
        location = location,
        publication = publication,
        participantCount = participantCount,
        createdAt = createdAt
    )

    /** Тождество — [id]: переименованная аптечка остаётся той же аптечкой. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is MedKit && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "MedKit(id=$id, name=$name, publication=$publication)"

}
