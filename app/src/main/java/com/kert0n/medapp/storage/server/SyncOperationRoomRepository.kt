package com.kert0n.medapp.storage.server

import com.kert0n.medapp.network.server.SyncCommand
import com.kert0n.medapp.network.server.SyncOperation
import com.kert0n.medapp.network.server.SyncOperationStatus
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

class SyncOperationRoomRepository @Inject constructor(
    private val queue: SyncOperationDao
) : SyncOperationStorageRepository {

    override suspend fun enqueue(
        id: Uuid,
        command: SyncCommand,
        at: Instant,
        groupId: Uuid?,
        dependsOn: Set<Uuid>
    ): SyncOperation = queue.enqueue(id, command, at, groupId, dependsOn)

    override suspend fun find(id: Uuid): SyncOperation? =
        (queue.find(id)?.toDomain() as? StoredSyncOperation.Readable)?.operation

    /** Нечитаемые сюда не попадают: их находит и называет [unreadable]. */
    override suspend fun withStatus(status: SyncOperationStatus): List<SyncOperation> =
        queue.withStatus(status).mapNotNull { (it.toDomain() as? StoredSyncOperation.Readable)?.operation }

    override suspend fun settle(
        id: Uuid,
        status: SyncOperationStatus,
        lastError: String?,
        at: Instant?,
        attempted: Boolean
    ) = queue.settle(id, status, lastError, at, if (attempted) 1 else 0)

    override suspend fun unreadable(): List<StoredSyncOperation.Unreadable> =
        queue.all().mapNotNull { it.toDomain() as? StoredSyncOperation.Unreadable }
}
