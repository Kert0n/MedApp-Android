package com.kert0n.medapp.network.server

import java.time.Instant
import java.util.Objects
import kotlin.uuid.Uuid

/**
 * Запись очереди: что предстоит доставить, чем это уже стало запросом и чем кончилось.
 *
 * `sequence` выдаёт база — номер принадлежит ей, а не команде, поэтому в самих командах его нет
 * (PLAN E2). Зависимости — множество: порядок между ними ничего не значит, и повтор тоже, а
 * список потребовал бы проверки уникальности вместо типа.
 */
class SyncOperation(
    val id: Uuid,
    val command: SyncCommand,
    val sequence: Long,
    val createdAt: Instant,
    val payloadVersion: Int,
    val prepared: PreparedRequest? = null,
    val groupId: Uuid? = null,
    dependsOn: Set<Uuid> = emptySet(),
    val status: SyncOperationStatus = SyncOperationStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val lastTriedAt: Instant? = null,
    val reconciledBy: Uuid? = null
) {
    /** Своя копия: множество, оставшееся у вызывающего, меняло бы порядок отправки очереди. */
    val dependsOn: Set<Uuid> = dependsOn.toSet()

    init {
        require(sequence >= 0) { "номер в очереди не бывает отрицательным: $sequence" }
        require(attempts >= 0) { "число попыток не бывает отрицательным: $attempts" }
        require(payloadVersion >= 1) { "версия payload начинается с единицы" }
        require(id !in dependsOn) { "операция не зависит от себя самой" }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SyncOperation &&
                id == other.id &&
                command == other.command &&
                sequence == other.sequence &&
                createdAt == other.createdAt &&
                payloadVersion == other.payloadVersion &&
                prepared == other.prepared &&
                groupId == other.groupId &&
                dependsOn == other.dependsOn &&
                status == other.status &&
                attempts == other.attempts &&
                lastError == other.lastError &&
                lastTriedAt == other.lastTriedAt &&
                reconciledBy == other.reconciledBy
            )

    override fun hashCode(): Int = Objects.hash(
        id, command, sequence, createdAt, payloadVersion, prepared, groupId, dependsOn,
        status, attempts, lastError, lastTriedAt, reconciledBy
    )

    override fun toString(): String =
        "SyncOperation(id=$id, sequence=$sequence, status=$status, command=$command)"
}
