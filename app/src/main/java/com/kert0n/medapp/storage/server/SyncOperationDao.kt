package com.kert0n.medapp.storage.server

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.SyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
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
            "AND status NOT IN ('APPLIED', 'REFUSED', 'ACCESS_LOST') " +
            "ORDER BY sequence"
    )
    suspend fun unclosedOfPackage(packageId: Uuid): List<SyncOperationStorageRow>

    @Transaction
    @Query("SELECT * FROM sync_operations WHERE status = :status ORDER BY sequence")
    suspend fun withStatus(status: SyncOperationStatus): List<SyncOperationStorageRow>

    /**
     * Готовые к отправке: ожидающие и отправлявшиеся в момент смерти процесса, у которых каждая
     * зависимость **применена** — зависимость значит «нужен эффект», и закрытая отказом её не
     * даёт. Порядок — номер очереди; кто ещё не готов, ждёт своей зависимости.
     */
    @Transaction
    @Query(
        "SELECT * FROM sync_operations o WHERE status IN ('PENDING', 'SENDING') " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM sync_operation_dependencies d JOIN sync_operations p ON p.id = d.depends_on_id " +
            "  WHERE d.operation_id = o.id AND p.status != 'APPLIED'" +
            ") ORDER BY sequence"
    )
    suspend fun ready(): List<SyncOperationStorageRow>

    /** Замораживает запрос и берёт в отправку — только если операция ещё не закрыта. */
    @Query(
        "UPDATE sync_operations SET status = 'SENDING', " +
            "prepared_method = :method, prepared_path = :path, prepared_query = :query, prepared_body = :body, " +
            "prepared_drug_version = :drugVersion, prepared_claims_version = :claimsVersion, " +
            "prepared_quantity_before = :quantityBefore, prepared_mine_before = :mineBefore, " +
            "prepared_unit_id = :unitId, prepared_at = :preparedAt " +
            "WHERE id = :id AND status IN ('PENDING', 'SENDING') AND prepared_method IS NULL"
    )
    suspend fun freeze(
        id: Uuid,
        method: String,
        path: String,
        query: String,
        body: String?,
        drugVersion: Long?,
        claimsVersion: Long?,
        quantityBefore: String?,
        mineBefore: String?,
        unitId: Uuid?,
        preparedAt: Instant
    ): Int

    @Query("UPDATE sync_operations SET status = 'SENDING' WHERE id = :id AND status IN ('PENDING', 'SENDING')")
    suspend fun markSending(id: Uuid): Int

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

    /**
     * Сбрасывает собранный запрос: версия устарела, и он готовится заново по свежему состоянию
     * под тем же номером. Не попытка — задержка от этого не растёт.
     */
    @Query(
        "UPDATE sync_operations SET status = 'PENDING', last_error = :lastError, last_tried_at = :at, " +
            "prepared_method = NULL, prepared_path = NULL, prepared_query = NULL, prepared_body = NULL, " +
            "prepared_drug_version = NULL, prepared_claims_version = NULL, prepared_quantity_before = NULL, " +
            "prepared_mine_before = NULL, prepared_unit_id = NULL, prepared_at = NULL " +
            "WHERE id = :id AND status = 'SENDING'"
    )
    suspend fun reprepare(id: Uuid, lastError: String, at: Instant): Int

    /**
     * Незакрытые операции, которым нужен эффект [dependsOn], закрываются тем же статусом: отказ
     * родителя отказывает зависимым, утрата доступа — теряет их. Возвращает их номера, чтобы
     * каскад дошёл и до их зависимых.
     */
    @Query(
        "SELECT operation_id FROM sync_operation_dependencies d JOIN sync_operations o ON o.id = d.operation_id " +
            "WHERE d.depends_on_id = :dependsOn AND o.status IN ('PENDING', 'SENDING')"
    )
    suspend fun unclosedDependentsOf(dependsOn: Uuid): List<Uuid>

    @Query("SELECT depends_on_id FROM sync_operation_dependencies WHERE operation_id = :id")
    suspend fun dependenciesOf(id: Uuid): List<Uuid>
}
