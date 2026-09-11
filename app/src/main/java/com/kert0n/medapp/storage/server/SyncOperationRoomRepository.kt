package com.kert0n.medapp.storage.server

import androidx.room.withTransaction
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.medkit.MedKitSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.pack.toDomain
import com.kert0n.medapp.network.pack.toPreparedRequest
import com.kert0n.medapp.network.medkit.toPreparedRequest as toMedKitPreparedRequest
import com.kert0n.medapp.queue.Delivery
import com.kert0n.medapp.queue.QueueStorage
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.SyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
import com.kert0n.medapp.queue.unknownRoot
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.medkit.MedKitDao
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Очередь в Room: и хранение операций для тех, кто их ставит, и [QueueStorage] для работника.
 * Транзакции очереди принадлежат ей: заморозка запроса вместе с переводом в отправку, закрытие
 * вместе с применением снимка и учётом расхода приёма (PLAN F5, E1).
 */
class SyncOperationRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val queue: SyncOperationDao,
    private val packages: PackageDao,
    private val intakes: IntakeDao,
    private val medKits: MedKitDao,
    private val vocabulary: VocabularyDao
) : SyncOperationStorageRepository, QueueStorage {

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

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation =
        queue.enqueue(queued.id, queued.command, at, queued.groupId, queued.dependsOn)

    override suspend fun ready(): List<StoredSyncOperation> = database.withTransaction {
        val words = vocabulary.snapshot()
        queue.ready().map { it.toDomain(words) }
    }

    /**
     * Предусловия берутся у пачки в этой же транзакции: версии, подтверждённый остаток и своя
     * бронь — то, что устройство считает правдой в момент первой отправки. Второй раз запрос не
     * собирается: `freeze` не трогает строку, где он уже есть.
     */
    override suspend fun take(id: Uuid, at: Instant): SyncOperation? = database.withTransaction {
        val words = vocabulary.snapshot()
        val stored = queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable
            ?: return@withTransaction null
        val operation = stored.operation
        if (operation.status == SyncOperationStatus.DONE || operation.status == SyncOperationStatus.ACCESS_LOST) {
            return@withTransaction null
        }
        if (operation.prepared == null) {
            val request = when (val command = operation.command) {
                is PackageSyncCommand -> {
                    val row = packages.find(command.packageId)
                    val pkg = row?.toDomain(words)
                    command.toPreparedRequest(
                        operationId = operation.id,
                        sync = row?.pack?.syncState() ?: PackageSyncState(command.packageId),
                        confirmed = pkg?.quantity,
                        mine = pkg?.claims?.mine?.let { Quantity(it, pkg.quantity.unit) },
                        at = at
                    )
                }
                is MedKitSyncCommand -> command.toMedKitPreparedRequest(at)
                else -> command.unknownRoot()
            }
            val columns = request.toStorageColumns()
            queue.freeze(
                id = id,
                method = columns.method,
                path = columns.path,
                query = columns.query,
                body = columns.body,
                drugVersion = columns.drugVersion,
                claimsVersion = columns.claimsVersion,
                quantityBefore = columns.quantityBefore,
                mineBefore = columns.mineBefore,
                unitId = columns.unitId,
                preparedAt = columns.at
            )
        } else {
            queue.markSending(id)
        }
        (queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable)?.operation
    }

    /**
     * Исход и его следствия одной транзакцией: статус операции, снимок пачки поверх подтверждённого
     * остатка и броней, учёт расхода у приёма, который эту операцию поставил (PLAN E1, F5).
     */
    override suspend fun settle(id: Uuid, outcome: Delivery, at: Instant) = database.withTransaction {
        val words = vocabulary.snapshot()
        val operation = (queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable)?.operation
            ?: return@withTransaction
        when (outcome) {
            is Delivery.Done -> {
                queue.settle(id, SyncOperationStatus.DONE, outcome.refusal, at, attempted = 1)
                intakes.markRemoteApplied(id)
                val command = operation.command as? PackageSyncCommand ?: return@withTransaction
                val snapshot = outcome.snapshot
                if (snapshot != null) {
                    // Аптечка снимка — объектом из базы; перенос мог сменить её, и берётся та,
                    // которую называет снимок.
                    val medKit = requireNotNull(medKits.find(snapshot.pack.medKitId)) {
                        "снимок пачки называет аптечку, которой нет: ${snapshot.pack.medKitId}"
                    }.toDomain()
                    val resolved = snapshot.toDomain(words, medKit, addedAt = at, observedAt = at)
                    packages.applyServerSnapshot(resolved.pack.toStorageEntity(resolved.sync), observedAt = at)
                    resolved.pack.claims?.let { packages.upsertClaims(it.toStorageEntity(command.packageId)) }
                } else {
                    // Пачки на сервере больше нет: истина — ноль, и локально она архивируется.
                    val row = packages.find(command.packageId) ?: return@withTransaction
                    val pkg = row.toDomain(words)
                    if (pkg.suppliesStock) {
                        val gone = pkg.correctTo(Quantity.zero(pkg.quantity.unit))
                        packages.save(gone.toStorageEntity(row.pack.syncState()), gone.toDetailsStorageEntity())
                    }
                    packages.deleteClaims(command.packageId)
                }
            }
            is Delivery.Retry ->
                queue.settle(id, SyncOperationStatus.PENDING, outcome.error, at, attempted = 1)
            Delivery.AccessLost -> {
                queue.settle(id, SyncOperationStatus.ACCESS_LOST, null, at, attempted = 1)
                (operation.command as? PackageSyncCommand)?.let { command ->
                    val row = packages.find(command.packageId) ?: return@withTransaction
                    val lost = row.toDomain(words).loseAccess()
                    packages.save(lost.toStorageEntity(row.pack.syncState()), lost.toDetailsStorageEntity())
                    packages.deleteClaims(command.packageId)
                }
            }
        }
    }
}
