package com.kert0n.medapp.storage.server

import com.kert0n.medapp.network.server.SyncCommand
import com.kert0n.medapp.network.server.SyncOperation
import com.kert0n.medapp.network.server.SyncOperationStatus
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Хранение очереди. Номер выдаёт база при постановке, поэтому команду ставят, а не сочиняют
 * операцию целиком (PLAN E2).
 */
interface SyncOperationStorageRepository {

    suspend fun enqueue(
        id: Uuid,
        command: SyncCommand,
        at: Instant,
        groupId: Uuid? = null,
        dependsOn: Set<Uuid> = emptySet()
    ): SyncOperation

    suspend fun find(id: Uuid): SyncOperation?

    suspend fun withStatus(status: SyncOperationStatus): List<SyncOperation>

    suspend fun settle(
        id: Uuid,
        status: SyncOperationStatus,
        lastError: String? = null,
        at: Instant? = null,
        attempted: Boolean = false
    )

    /**
     * Операции, которые нечем прочитать: чужая версия payload после обновления приложения.
     * Их переводят в `CONFLICT` решением человека, а не молча пропускают (PLAN F4).
     */
    suspend fun unreadable(): List<Uuid>
}
