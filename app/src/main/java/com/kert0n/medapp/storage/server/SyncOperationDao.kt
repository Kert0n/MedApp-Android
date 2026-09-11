package com.kert0n.medapp.storage.server

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kert0n.medapp.network.server.SyncCommand
import com.kert0n.medapp.network.server.SyncOperation
import com.kert0n.medapp.network.server.SyncOperationStatus
import java.time.Instant
import kotlin.uuid.Uuid

@Dao
interface SyncOperationDao {

    /**
     * Номер выдаёт база: он монотонен и уникален, а `UNIQUE` ловит гонку двух постановок.
     * Команда своего номера не знает — он принадлежит очереди, а не тому, что предстоит
     * доставить (PLAN E2).
     */
    @Transaction
    suspend fun enqueue(
        id: Uuid,
        command: SyncCommand,
        createdAt: Instant,
        groupId: Uuid? = null,
        dependsOn: Set<Uuid> = emptySet()
    ): SyncOperation {
        val operation = SyncOperation(
            id = id,
            command = command,
            sequence = (lastSequence() ?: -1L) + 1L,
            createdAt = createdAt,
            payloadVersion = SyncCommandStorageConverter.PAYLOAD_VERSION,
            groupId = groupId,
            dependsOn = dependsOn
        )
        insert(operation.toStorageEntity())
        insertDependencies(
            dependsOn.map { SyncOperationDependencyStorageEntity(id, it) }
        )
        return operation
    }

    @Query("SELECT MAX(sequence) FROM sync_operations")
    suspend fun lastSequence(): Long?

    @Insert
    suspend fun insert(operation: SyncOperationStorageEntity)

    @Insert
    suspend fun insertDependencies(dependencies: List<SyncOperationDependencyStorageEntity>)

    @Update
    suspend fun update(operation: SyncOperationStorageEntity)

    @Transaction
    @Query("SELECT * FROM sync_operations WHERE id = :id")
    suspend fun find(id: Uuid): SyncOperationStorageRow?

    @Transaction
    @Query("SELECT * FROM sync_operations ORDER BY sequence")
    suspend fun all(): List<SyncOperationStorageRow>

    /** Порядок по одной упаковке строится запросом, а не доменной функцией (PLAN E2). */
    @Transaction
    @Query("SELECT * FROM sync_operations WHERE package_id = :packageId ORDER BY sequence")
    suspend fun ofPackage(packageId: Uuid): List<SyncOperationStorageRow>

    /** Незакрытые операции пачки. Чтение, а не поток: оценка количества складывается не из них одних. */
    @Transaction
    @Query(
        "SELECT * FROM sync_operations WHERE package_id = :packageId " +
            "AND status NOT IN ('DONE', 'ACCESS_LOST') " +
            "ORDER BY sequence"
    )
    suspend fun unclosedOfPackage(packageId: Uuid): List<SyncOperationStorageRow>

    @Transaction
    @Query("SELECT * FROM sync_operations WHERE status = :status ORDER BY sequence")
    suspend fun withStatus(status: SyncOperationStatus): List<SyncOperationStorageRow>

    @Query(
        "UPDATE sync_operations SET status = :status, last_error = :lastError, " +
            "last_tried_at = :at, attempts = attempts + :attempted WHERE id = :id"
    )
    suspend fun settle(
        id: Uuid,
        status: SyncOperationStatus,
        lastError: String? = null,
        at: Instant? = null,
        attempted: Int = 0
    )

    @Query("SELECT depends_on_id FROM sync_operation_dependencies WHERE operation_id = :id")
    suspend fun dependenciesOf(id: Uuid): List<Uuid>
}
