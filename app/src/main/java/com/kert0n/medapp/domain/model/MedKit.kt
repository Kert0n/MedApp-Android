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
 * названия даёт другую аптечку. Собрать её можно двумя названными путями: [create] заводит
 * локальную, [restore] восстанавливает сохранённую.
 */
class MedKit private constructor(
    val id: Uuid,                   // придуман клиентом; он же серверный
    val name: String,               // 1..200, только на устройстве
    val location: String?,          // ≤300, место хранения; только на устройстве
    val publication: KitPublication,
    val participantCount: Long,     // 1 у локальной, иначе userCount с сервера
    val createdAt: Instant,
    val syncedAt: Instant?
) {

    init {
        requireText(name, MED_KIT_NAME_MAX_LENGTH, "MedKit.name")
        requireOptionalText(location, MED_KIT_LOCATION_MAX_LENGTH, "MedKit.location")
        require(participantCount >= 1) { "участник всегда есть хотя бы один — я сам" }
        if (publication == KitPublication.LOCAL) {
            require(participantCount == 1L) { "у локальной аптечки других участников нет" }
            require(syncedAt == null) { "локальная аптечка с сервером не говорила" }
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
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    /** Тождество — [id]: переименованная аптечка остаётся той же аптечкой. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is MedKit && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "MedKit(id=$id, name=$name, publication=$publication)"

    companion object {

        /**
         * Заведение локальной аптечки. Публикация — отдельный сценарий (PLAN E5), поэтому новая
         * аптечка всегда `LOCAL` с единственным участником, а не «пока непонятно какая».
         */
        fun create(id: Uuid, name: String, location: String?, createdAt: Instant): MedKit =
            MedKit(
                id = id,
                name = name,
                location = location,
                publication = KitPublication.LOCAL,
                participantCount = 1,
                createdAt = createdAt,
                syncedAt = null
            )

        /**
         * Восстановление сохранённого состояния: строка базы вместе с числом участников из
         * снимка. Не бизнес-переход — оно ничего не решает, а возвращает уже решённое.
         */
        fun restore(
            id: Uuid,
            name: String,
            location: String?,
            publication: KitPublication,
            participantCount: Long,
            createdAt: Instant,
            syncedAt: Instant?
        ): MedKit = MedKit(id, name, location, publication, participantCount, createdAt, syncedAt)
    }
}
