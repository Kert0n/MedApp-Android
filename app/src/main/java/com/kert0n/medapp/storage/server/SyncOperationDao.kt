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
     * Готовые к работе: ожидающие, отправлявшиеся в момент смерти процесса и получившие ответ,
     * который ещё не применён, — у которых каждая зависимость **применена** — зависимость значит «нужен эффект», и закрытая отказом её не
     * даёт. Порядок — номер очереди; кто ещё не готов, ждёт своей зависимости.
     */
    @Transaction
    @Query(
        "SELECT * FROM sync_operations o WHERE status IN ('PENDING', 'SENDING', 'ANSWERED') " +
            "AND (not_before IS NULL OR not_before <= :now) " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM sync_operation_dependencies d JOIN sync_operations p ON p.id = d.depends_on_id " +
            "  WHERE d.operation_id = o.id AND p.status != 'APPLIED'" +
            ") AND NOT EXISTS (" +
            "  SELECT 1 FROM sync_operations e WHERE e.package_id = o.package_id AND e.sequence < o.sequence " +
            "  AND e.status IN ('PENDING', 'SENDING', 'ANSWERED')" +
            ") ORDER BY sequence"
    )
    suspend fun ready(now: Instant): List<SyncOperationStorageRow>

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

    /**
     * Берёт замороженный запрос в отправку снова. Операция, которую застали в `SENDING`, — прошлый
     * полёт умер вместе с процессом, и его исход неизвестен: факт остаётся у запроса.
     */
    @Query(
        "UPDATE sync_operations SET outcome_unknown = CASE WHEN status = 'SENDING' THEN 1 ELSE outcome_unknown END, " +
            "status = 'SENDING' WHERE id = :id AND status IN ('PENDING', 'SENDING')"
    )
    suspend fun markSending(id: Uuid): Int

    /** Ответ записан до применения: полученное подтверждение не теряется. Только из отправки. */
    @Query(
        "UPDATE sync_operations SET status = 'ANSWERED', answer_status = :answerStatus, answer_body = :answerBody, " +
            "last_tried_at = :at WHERE id = :id AND status = 'SENDING'"
    )
    suspend fun answered(id: Uuid, answerStatus: Int, answerBody: String, at: Instant): Int

    /** Ответ есть, применить нечем: остаётся `ANSWERED`, попытка считается — от неё растёт задержка. */
    @Query(
        "UPDATE sync_operations SET last_error = :lastError, last_tried_at = :at, attempts = attempts + 1, " +
            "not_before = :notBefore WHERE id = :id AND status = 'ANSWERED'"
    )
    suspend fun defer(id: Uuid, lastError: String, at: Instant, notBefore: Instant): Int

    /**
     * Закрытие или возврат в ожидание — только незакрытой: закрытая второй раз не закрывается.
     * Записанный ответ стирается: он либо применён, либо будет получен заново. Неизвестный исход
     * прилипает к запросу: раз неизвестный — неизвестный, пока запрос не переподготовлен.
     */
    @Query(
        "UPDATE sync_operations SET status = :status, last_error = :lastError, " +
            "last_tried_at = :at, attempts = attempts + :attempted, answer_status = NULL, answer_body = NULL, " +
            "not_before = :notBefore, outcome_unknown = MAX(outcome_unknown, :outcomeUnknown) " +
            "WHERE id = :id AND status IN ('PENDING', 'SENDING', 'ANSWERED')"
    )
    suspend fun settle(
        id: Uuid,
        status: SyncOperationStatus,
        lastError: String? = null,
        at: Instant? = null,
        attempted: Int = 0,
        notBefore: Instant? = null,
        outcomeUnknown: Int = 0
    ): Int

    /**
     * Сбрасывает собранный запрос: версия устарела, и он готовится заново по свежему состоянию
     * под тем же номером. Не попытка — задержка от этого не растёт, и счёт попыток остаётся у
     * операции. Факт «исход неизвестен» принадлежит **запросу** и умирает вместе с ним: по нему
     * расход решает, значит ли 404 «мы сами опустошили пачку» (PLAN E3).
     */
    @Query(
        "UPDATE sync_operations SET status = 'PENDING', last_error = :lastError, last_tried_at = :at, not_before = :notBefore, outcome_unknown = 0, " +
            "prepared_method = NULL, prepared_path = NULL, prepared_query = NULL, prepared_body = NULL, " +
            "prepared_drug_version = NULL, prepared_claims_version = NULL, prepared_quantity_before = NULL, " +
            "prepared_mine_before = NULL, prepared_unit_id = NULL, prepared_at = NULL, " +
            "answer_status = NULL, answer_body = NULL " +
            "WHERE id = :id AND status IN ('SENDING', 'ANSWERED')"
    )
    suspend fun reprepare(id: Uuid, lastError: String, at: Instant, notBefore: Instant?): Int

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
