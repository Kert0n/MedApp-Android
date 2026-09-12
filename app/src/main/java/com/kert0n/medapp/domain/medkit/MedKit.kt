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

    /** Как аптечку видит чужой агрегат: тождество и публикация, без переходов. */
    val ref: MedKitRef get() = MedKitRef(id, publication)

    /** Как аптечку видит экран: величина, наружу уходит она, а не сущность. */
    fun projection(): MedKitProjection = MedKitProjection(
        id = id,
        name = name,
        location = location,
        publication = publication,
        participantCount = participantCount,
        createdAt = createdAt,
        isShared = isShared,
        acceptsInvitations = acceptsInvitations
    )

    /**
     * Отдельно от [isShared]: аптечка, из которой ушли все, кроме меня, остаётся серверной и
     * приглашения выдаёт. Приглашать в местную некуда — на сервере её нет (PLAN D2).
     */
    val acceptsInvitations: Boolean get() = publication == Publication.PUBLISHED

    /**
     * Аптечка оказалась на сервере — целиком, вместе с пачками: половины не бывает, поэтому
     * состояние меняется одним переходом, а не «начали публиковать». Обратной дороги нет (E5).
     */
    fun publish(): MedKit {
        check(publication == Publication.LOCAL) { "аптечка уже на сервере" }
        return MedKit(
            id = id,
            name = name,
            location = location,
            publication = Publication.PUBLISHED,
            participantCount = participantCount,
            createdAt = createdAt
        )
    }

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

    /**
     * Где аптечка существует. Состояний два, потому что публикация — одно действие при связи:
     * либо аптечка на сервере целиком, либо её там нет; обрыв посреди откатывается `DELETE`
     * (PLAN E5).
     */
    enum class Publication {
        LOCAL,        // на сервере не существует
        PUBLISHED     // существует на сервере
    }

    companion object {
        const val NAME_MAX_LENGTH = 200
        const val LOCATION_MAX_LENGTH = 300
    }
}
