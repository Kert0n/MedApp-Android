package com.kert0n.medapp.domain.medkit

import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Аптечка — место хранения упаковок. Сущность: переименованная аптечка — та же аптечка, равенство
 * по [id]. Название и место хранения остаются на устройстве; сервер знает только существование,
 * участие и число участников (PLAN C0, E5).
 */
class MedKit(
    val id: Uuid,                   // придуман клиентом; он же серверный
    val name: String,               // 1..200, только на устройстве
    val location: String?,          // ≤300, место хранения; только на устройстве
    val publication: Publication,
    val participantCount: Long,     // 1 у локальной, иначе userCount с сервера
    val createdAt: Instant
) {

    init {
        requireText(name, NAME_MAX_LENGTH, "MedKit.name")
        requireOptionalText(location, LOCATION_MAX_LENGTH, "MedKit.location")
        require(participantCount >= 1) { "участник всегда есть хотя бы один — я сам" }
        if (publication == Publication.LOCAL) {
            require(participantCount == 1L) { "у локальной аптечки других участников нет" }
        }
    }

    val isShared: Boolean get() = participantCount > 1

    /**
     * Отдельно от [isShared]. Пока группа операций публикации не завершена целиком, часть пачек
     * на сервере уже есть, а часть нет: приглашённый увидел бы половину аптечки (PLAN D2).
     */
    val acceptsInvitations: Boolean get() = publication == Publication.PUBLISHED

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

    enum class Publication {
        LOCAL,        // на сервере не существует
        PUBLISHING,   // группа операций публикации ещё не завершена
        PUBLISHED     // существует на сервере
    }

    companion object {
        const val NAME_MAX_LENGTH = 200
        const val LOCATION_MAX_LENGTH = 300
    }
}
