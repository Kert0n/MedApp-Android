package com.kert0n.medapp.storage.server

import androidx.room.withTransaction
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.pack.toDomain
import com.kert0n.medapp.queue.pack.prepare
import com.kert0n.medapp.queue.medkit.toPreparedRequest as toMedKitPreparedRequest
import com.kert0n.medapp.queue.Delivery
import com.kert0n.medapp.queue.PackageState
import com.kert0n.medapp.queue.Preparation
import com.kert0n.medapp.queue.Take
import com.kert0n.medapp.queue.QueueStorage
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.RefusalReason
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
    ) {
        queue.settle(id, status, lastError, at, if (attempted) 1 else 0)
    }

    override suspend fun unreadable(): List<StoredSyncOperation.Unreadable> =
        queue.all().let { rows ->
            val words = vocabulary.snapshot()
            rows.mapNotNull { it.toDomain(words) as? StoredSyncOperation.Unreadable }
        }

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation =
        queue.enqueue(queued.id, queued.command, at, queued.groupId, queued.dependsOn)

    override suspend fun ready(now: Instant): List<StoredSyncOperation> = database.withTransaction {
        val words = vocabulary.snapshot()
        queue.ready(now).map { it.toDomain(words) }
    }

    /**
     * Свежее состояние ложится в базу первым, предусловия берутся у пачки после этого — в той же
     * транзакции: версии, подтверждённый остаток и своя бронь — то, что у сервера сейчас (PLAN
     * E2, E3). Второй раз запрос не собирается: `freeze` не трогает строку, где он уже есть.
     */
    override suspend fun take(id: Uuid, fresh: PackageSnapshotNetworkDTO?, at: Instant): Take? = database.withTransaction {
        val words = vocabulary.snapshot()
        val stored = queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable
            ?: return@withTransaction null
        val operation = stored.operation
        // Берётся только ожидающая или отправлявшаяся: закрытая и получившая ответ — нет.
        if (operation.status != SyncOperationStatus.PENDING && operation.status != SyncOperationStatus.SENDING) {
            return@withTransaction null
        }
        fresh?.let { apply(it, words, at) }
        if (operation.prepared == null) {
            val request = when (val command = operation.command) {
                is PackageSyncCommand -> {
                    val row = packages.find(command.packageId)
                        ?: return@withTransaction Take.Closed(Delivery.AccessLost).also { settle(id, it.delivery, at) }
                    val pkg = row.toDomain(words)
                    when (val prepared = command.prepare(operation.id, pkg, row.pack.syncState(), at)) {
                        is Preparation.Request -> prepared.request
                        is Preparation.Refuse -> return@withTransaction closedByPreparation(
                            id, Delivery.Refused(prepared.reason, PackageState.None), at
                        )
                        Preparation.AlreadyApplied -> return@withTransaction closedByPreparation(
                            id, Delivery.Applied(PackageState.None), at
                        )
                    }
                }
                is MedKitSyncCommand -> command.toMedKitPreparedRequest(at)
                else -> command.unknownRoot()
            }
            val columns = request.toStorageColumns()
            val frozen = queue.freeze(
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
            // Ноль строк — операцию закрыли или взяли между чтением и взятием: не наша.
            if (frozen == 0) return@withTransaction null
        } else {
            if (queue.markSending(id) == 0) return@withTransaction null
        }
        (queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable)?.operation?.let { Take.Sending(it) }
    }

    override suspend fun answered(id: Uuid, answer: RawResponse, at: Instant) {
        queue.answered(id, answer.status, answer.body, at)
    }

    override suspend fun defer(id: Uuid, reason: String, at: Instant, notBefore: Instant) {
        queue.defer(id, reason, at, notBefore)
    }

    /** Подготовка закрыла операцию сама: истина по пачке уже в базе — она только что легла свежим снимком. */
    private suspend fun closedByPreparation(id: Uuid, delivery: Delivery, at: Instant): Take {
        settle(id, delivery, at)
        return Take.Closed(delivery)
    }

    /**
     * Снимок поверх подтверждённого остатка и броней. Аптечка снимка — объектом из базы: перенос
     * мог сменить её, и берётся та, которую называет снимок.
     */
    private suspend fun apply(snapshot: PackageSnapshotNetworkDTO, words: Vocabulary, at: Instant) {
        val medKit = requireNotNull(medKits.find(snapshot.pack.medKitId)) {
            "снимок пачки называет аптечку, которой нет: ${snapshot.pack.medKitId}"
        }.toDomain()
        val resolved = snapshot.toDomain(words, medKit, addedAt = at, observedAt = at)
        // Запоздалый снимок свежий не перекрывает — ни состояние, ни брони.
        if (packages.applyServerSnapshot(resolved.pack.toStorageEntity(resolved.sync), observedAt = at)) {
            resolved.pack.claims?.let { packages.upsertClaims(it.toStorageEntity(snapshot.pack.id)) }
        }
    }

    /**
     * Исход и его следствия одной транзакцией: статус операции, снимок пачки поверх подтверждённого
     * остатка и броней, учёт расхода у приёма, который эту операцию поставил, и судьба зависимых
     * (PLAN E1, E3, F5). «Устарело» операцию не закрывает: снимок ложится, запрос сбрасывается,
     * и она снова ждёт.
     */
    override suspend fun settle(id: Uuid, outcome: Delivery, at: Instant) = database.withTransaction {
        val words = vocabulary.snapshot()
        val operation = (queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable)?.operation
            ?: return@withTransaction
        val command = operation.command as? PackageSyncCommand
        // Закрытие одно: строка, которую уже закрыли, второй раз не закрывается, и следствий у
        // второго закрытия нет — условие стоит в самом запросе.
        when (outcome) {
            is Delivery.Applied -> {
                if (queue.settle(id, SyncOperationStatus.APPLIED, null, at, attempted = 1) == 0) return@withTransaction
                intakes.markRemoteApplied(id)
                command?.let { apply(outcome.state, it, words, at) }
            }
            is Delivery.Stale -> {
                if (queue.reprepare(id, lastError = "устарело: ${outcome.snapshot.pack.version}", at = at, notBefore = outcome.notBefore) == 0) {
                    return@withTransaction
                }
                apply(outcome.snapshot, words, at)
            }
            is Delivery.Refused -> {
                if (queue.settle(id, SyncOperationStatus.REFUSED, outcome.reason.name, at, attempted = 1) == 0) return@withTransaction
                intakes.markRemoteRefused(id)
                command?.let { apply(outcome.state, it, words, at) }
                cascade(id, SyncOperationStatus.REFUSED)
            }
            is Delivery.Retry -> {
                queue.settle(id, SyncOperationStatus.PENDING, outcome.error, at, attempted = 1, notBefore = outcome.notBefore)
                Unit
            }
            Delivery.AccessLost -> {
                if (queue.settle(id, SyncOperationStatus.ACCESS_LOST, null, at, attempted = 1) == 0) return@withTransaction
                intakes.markRemoteRefused(id)
                command?.let {
                    val row = packages.find(it.packageId) ?: return@let
                    val lost = row.toDomain(words).loseAccess()
                    packages.save(lost.toStorageEntity(row.pack.syncState()), lost.toDetailsStorageEntity())
                    packages.deleteClaims(it.packageId)
                }
                cascade(id, SyncOperationStatus.ACCESS_LOST)
            }
        }
    }

    /** Истина по пачке после закрытия — снимок, «пачки нет» либо ничего. */
    private suspend fun apply(state: PackageState, command: PackageSyncCommand, words: Vocabulary, at: Instant) {
        when (state) {
            is PackageState.Present -> apply(state.snapshot, words, at)
            PackageState.Gone -> {
                // Пачки на сервере больше нет: истина — ноль, и локально она архивируется.
                val row = packages.find(command.packageId) ?: return
                val pkg = row.toDomain(words)
                if (pkg.suppliesStock) {
                    val gone = pkg.correctTo(Quantity.zero(pkg.quantity.unit))
                    packages.save(gone.toStorageEntity(row.pack.syncState()), gone.toDetailsStorageEntity())
                }
                packages.deleteClaims(command.packageId)
            }
            PackageState.None -> Unit
        }
    }

    /**
     * Зависимость значит «нужен эффект»: операции, которым нужен был эффект закрытой отказом или
     * потерей доступа, закрываются тем же статусом — и их зависимые следом.
     */
    private suspend fun cascade(id: Uuid, status: SyncOperationStatus) {
        val pending = ArrayDeque(listOf(id))
        while (pending.isNotEmpty()) {
            for (dependent in queue.unclosedDependentsOf(pending.removeFirst())) {
                queue.settle(dependent, status, RefusalReason.SUPERSEDED.name, at = null, attempted = 0)
                intakes.markRemoteRefused(dependent)
                pending += dependent
            }
        }
    }
}
