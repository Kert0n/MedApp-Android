package com.kert0n.medapp.storage.server

import androidx.room.withTransaction
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.queue.Delivery
import com.kert0n.medapp.queue.PackageState
import com.kert0n.medapp.queue.Preparation
import com.kert0n.medapp.queue.QueueStorage
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Settlement
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncOperation
import com.kert0n.medapp.queue.SyncOperationStatus
import com.kert0n.medapp.queue.Take
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.queue.pack.prepare
import com.kert0n.medapp.queue.settlement
import com.kert0n.medapp.queue.unknownRoot
import com.kert0n.medapp.queue.medkit.toPreparedRequest as toMedKitPreparedRequest
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.dropSource
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.medkit.MedKitDao
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.applySnapshot
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Порт очереди в Room — [QueueStorage] для работника. Транзакции очереди принадлежат ей: заморозка
 * запроса вместе с переводом в отправку, применение исхода вместе со всеми его эффектами (PLAN F5).
 * Что исход значит, решено в очереди ([Settlement]); здесь эффекты только применяются, и
 * ветвления по видам доставки нет. Строки очереди для тех, кто их ставит и читает, —
 * [SyncOperationRoomRepository].
 */
class QueueRoomStorage @Inject constructor(
    private val database: MedAppDatabase,
    private val queue: SyncOperationDao,
    private val packages: PackageDao,
    private val intakes: IntakeDao,
    private val medKits: MedKitDao,
    private val courses: CourseDao,
    private val movements: StockMovementDao,
    private val vocabulary: VocabularyDao
) : QueueStorage {

    /** Room сообщает об изменении таблицы после коммита — то, что outbox и должен услышать. */
    override fun changes(): Flow<Unit> =
        database.invalidationTracker.createFlow("sync_operations", emitInitialState = false).map { }

    override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation =
        queue.enqueue(queued.id, queued.command, at, queued.groupId, queued.dependsOn)

    override suspend fun ready(now: Instant): List<StoredSyncOperation> = database.withTransaction {
        val words = vocabulary.snapshot()
        queue.ready(now).map { it.toDomain(words) }
    }

    override suspend fun nextDueAt(now: Instant): Instant? = queue.nextDueAt(now)

    override suspend fun medKit(id: Uuid): MedKitRef? = medKits.find(id)?.toRef()

    /**
     * Свежее состояние ложится в базу первым, предусловия берутся у пачки после этого — в той же
     * транзакции: версии, подтверждённый остаток и своя бронь — то, что у сервера сейчас (PLAN
     * E2, E3). Второй раз запрос не собирается: `freeze` не трогает строку, где он уже есть.
     */
    override suspend fun take(id: Uuid, fresh: PackageSnapshot?, at: Instant): Take? = database.withTransaction {
        val words = vocabulary.snapshot()
        val stored = queue.find(id)?.toDomain(words) as? StoredSyncOperation.Readable
            ?: return@withTransaction null
        val operation = stored.operation
        // Берётся только ожидающая или отправлявшаяся: закрытая и получившая ответ — нет.
        if (operation.status != SyncOperationStatus.PENDING && operation.status != SyncOperationStatus.SENDING) {
            return@withTransaction null
        }
        fresh?.let { layDown(it, at) }
        if (operation.prepared == null) {
            val request = when (val command = operation.command) {
                is PackageSyncCommand -> {
                    val row = packages.find(command.packageId)
                        ?: return@withTransaction closedByPreparation(operation, Delivery.AccessLost, at)
                    val pkg = row.toDomain(words)
                    when (val prepared = command.prepare(operation.id, pkg, row.pack.syncState(), at)) {
                        is Preparation.Request -> prepared.request
                        is Preparation.Refuse -> return@withTransaction closedByPreparation(
                            operation, Delivery.Refused(prepared.reason, PackageState.None), at
                        )
                        Preparation.AlreadyApplied -> return@withTransaction closedByPreparation(
                            operation, Delivery.Applied(PackageState.None), at
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
    private suspend fun closedByPreparation(operation: SyncOperation, delivery: Delivery, at: Instant): Take {
        settle(operation.id, delivery.settlement(operation.command), at)
        return Take.Closed(delivery)
    }

    /**
     * Переход и его эффекты одной транзакцией. Закрытие одно: строка, которую уже закрыли, второй
     * раз не закрывается, и следствий у второго закрытия нет — условие стоит в самом запросе.
     */
    override suspend fun settle(id: Uuid, settlement: Settlement, at: Instant) = database.withTransaction {
        val changed = when (val transition = settlement.transition) {
            // Закрытая операция не повторяется, а счёт попыток — вход задержки и только он:
            // закрытию нечего им двигать (PLAN E2, E3).
            is Settlement.Transition.Close ->
                queue.settle(id, transition.status, transition.lastError, at, attempted = 0)
            is Settlement.Transition.Reprepare ->
                queue.reprepare(id, transition.lastError, at, transition.notBefore)
            is Settlement.Transition.Retry -> queue.settle(
                id, SyncOperationStatus.PENDING, transition.lastError, at,
                attempted = if (transition.attempted) 1 else 0,
                notBefore = transition.notBefore,
                outcomeUnknown = if (transition.outcomeUnknown) 1 else 0
            )
        }
        if (changed == 0) return@withTransaction
        for (effect in settlement.effects) apply(id, effect, at)
    }

    private suspend fun apply(id: Uuid, effect: Settlement.Effect, at: Instant) {
        when (effect) {
            is Settlement.Effect.LayDown -> layDown(effect.snapshot, at)
            // Коробки у нас больше нет — строки не остаётся, курс теряет источник (D3, D5).
            // На сервере её нет по нашей же причине — о количестве это не говорит ничего (D7);
            // утрачен доступ — последний виденный остаток уходит из учёта записью в историю.
            is Settlement.Effect.PackageGone -> gone(effect.packageId, movement = null, at)
            is Settlement.Effect.PackageLost -> gone(effect.packageId, movement = { it.lost(Uuid.random(), at) }, at)
            is Settlement.Effect.Account -> intakes.setAccounting(id, effect.accounting)
            is Settlement.Effect.Cascade -> cascade(id, effect)
        }
    }

    private suspend fun gone(
        packageId: Uuid,
        movement: ((Package) -> StockMovement)?,
        at: Instant
    ) {
        val words = vocabulary.snapshot()
        val pkg = packages.find(packageId)?.toDomain(words) ?: return
        movement?.let { movements.insert(it(pkg).toMovementStorageEntity()) }
        courses.dropSource(pkg.ref, words, at)
        packages.delete(packageId)
    }

    /** Разрешённый снимок поверх подтверждённого остатка и броней; разрешать здесь нечего. */
    private suspend fun layDown(snapshot: PackageSnapshot, at: Instant) {
        packages.applySnapshot(snapshot, observedAt = at)
    }

    /** Зависимость значит «нужен эффект»: не будет его у родителя — не будет и у зависимых, и у их зависимых. */
    private suspend fun cascade(id: Uuid, effect: Settlement.Effect.Cascade) {
        val pending = ArrayDeque(listOf(id))
        while (pending.isNotEmpty()) {
            for (dependent in queue.unclosedDependentsOf(pending.removeFirst())) {
                queue.settle(dependent, effect.status, com.kert0n.medapp.queue.RefusalReason.SUPERSEDED.name, at = null, attempted = 0)
                intakes.setAccounting(dependent, effect.accounting)
                pending += dependent
            }
        }
    }
}
