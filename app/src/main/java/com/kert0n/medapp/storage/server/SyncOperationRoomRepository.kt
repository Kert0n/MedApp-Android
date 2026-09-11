package com.kert0n.medapp.storage.server

import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.SyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

class SyncOperationRoomRepository @Inject constructor(
    private val queue: SyncOperationDao,
    private val vocabulary: VocabularyDao
) : SyncOperationStorageRepository {

    override suspend fun enqueue(
        id: Uuid,
        command: SyncCommand,
        at: Instant,
        groupId: Uuid?,
        dependsOn: Set<Uuid>
    ): SyncOperation = queue.enqueue(id, command, at, groupId, dependsOn)

    override suspend fun find(id: Uuid): SyncOperation? =
        (queue.find(id)?.toDomain(vocabulary.snapshot()) as? StoredSyncOperation.Readable)?.operation

    /** Нечитаемые сюда не попадают: их находит и называет [unreadable]. */
    override suspend fun withStatus(status: SyncOperationStatus): List<SyncOperation> =
        queue.withStatus(status).let { rows ->
            val words = vocabulary.snapshot()
            rows.mapNotNull { (it.toDomain(words) as? StoredSyncOperation.Readable)?.operation }
        }

    override suspend fun settle(
        id: Uuid,
        status: SyncOperationStatus,
        lastError: String?,
        at: Instant?,
        attempted: Boolean
    ) = queue.settle(id, status, lastError, at, if (attempted) 1 else 0)

    override suspend fun unreadable(): List<StoredSyncOperation.Unreadable> =
        queue.all().let { rows ->
            val words = vocabulary.snapshot()
            rows.mapNotNull { it.toDomain(words) as? StoredSyncOperation.Unreadable }
        }
}
