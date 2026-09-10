package com.kert0n.medapp.storage.pack

import androidx.room.withTransaction
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAvailability
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.pack.PackageQueueState
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.SyncOperationStatus
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.CourseReallocation
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.server.QueuedCommand
import com.kert0n.medapp.storage.server.StoredSyncOperation
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.server.SyncOperationStorageRow
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PackageRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val packages: PackageDao,
    private val courses: CourseDao,
    private val movements: StockMovementDao,
    private val queue: SyncOperationDao
) : PackageStorageRepository {

    override fun observe(id: Uuid): Flow<Package?> =
        packages.observe(id).map { it?.toDomain() }

    override suspend fun find(id: Uuid): Package? = packages.find(id)?.toDomain()

    override fun observeAvailability(id: Uuid): Flow<PackageAvailability?> =
        onChange { availabilityOf(id) }

    override fun list(query: PackageQuery, today: LocalDate): Flow<List<Package>> =
        onChange { listing(query, today) }

    override suspend fun add(pkg: Package, sync: PackageSyncState) =
        packages.save(pkg.toStorageEntity(sync), pkg.toDetailsStorageEntity())

    override suspend fun describe(packageId: Uuid, facts: PackageFacts): Boolean =
        change(packageId) { it.describe(facts) }

    override suspend fun loseAccess(packageId: Uuid): Boolean = database.withTransaction {
        val changed = change(packageId) { it.loseAccess() }
        if (changed) packages.deleteClaims(packageId)
        changed
    }

    /**
     * Переход применяется к тому, что лежит в базе, и пишется вместе с сохранённой обвязкой:
     * версии и время сверки принадлежат снимку сервера, а не действию человека (PLAN E4).
     */
    private suspend fun change(packageId: Uuid, transition: (Package) -> Package): Boolean =
        database.withTransaction {
            val stored = packages.find(packageId) ?: return@withTransaction false
            val changed = transition(stored.toDomain())
            packages.save(
                changed.toStorageEntity(stored.pack.syncState()),
                changed.toDetailsStorageEntity()
            )
            true
        }

    override suspend fun applyServerSnapshot(
        pkg: Package,
        sync: PackageSyncState,
        observedAt: Instant
    ) = packages.applyServerSnapshot(pkg.toStorageEntity(sync), observedAt)

    override suspend fun saveClaims(packageId: Uuid, claims: Claims?) {
        if (claims == null) packages.deleteClaims(packageId)
        else packages.upsertClaims(claims.toStorageEntity(packageId))
    }

    override suspend fun adjust(
        adjustment: PackageAdjustment,
        reallocation: CourseReallocation?,
        command: QueuedCommand?,
        at: Instant
    ): Boolean = database.withTransaction {
        val stored = packages.find(adjustment.packageId) ?: return@withTransaction false
        val applied = adjustment.applyTo(stored.toDomain(), at)
        // Версии и время сверки остаются те, что записал снимок сервера: их двигает сеть (E4).
        packages.save(
            applied.pack.toStorageEntity(stored.pack.syncState()),
            applied.pack.toDetailsStorageEntity()
        )
        movements.insert(applied.movement.toMovementStorageEntity())
        reallocation?.let { (plan, expected) ->
            courses.updateAllocations(
                plan.toCourseStorageEntity(),
                plan.medicine.toSourceStorageEntities(plan.id),
                expected
            )
        }
        command?.let {
            queue.enqueue(
                id = it.id,
                command = it.command,
                createdAt = at,
                groupId = it.groupId,
                dependsOn = it.dependsOn
            )
        }
        true
    }

    /**
     * Проекция читается одним снимком: поток лишь уведомляет, что база изменилась, а
     * согласованный набор входных данных берётся транзакцией. `combine` независимых потоков
     * этого не даёт — его значения относятся к разным состояниям базы, и экран получал бы
     * комбинацию, которой в базе никогда не было: свежий остаток со старой очередью.
     */
    private fun <T> onChange(read: suspend () -> T): Flow<T> =
        database.invalidationTracker.createFlow(*AVAILABILITY_TABLES).map { read() }

    private suspend fun availabilityOf(id: Uuid): PackageAvailability? = database.withTransaction {
        val pkg = packages.find(id)?.toDomain() ?: return@withTransaction null
        availabilityOf(
            pkg,
            queue.unclosedOfPackage(id),
            packages.allocationsOf(listOf(id)).firstOrNull()
        )
    }

    /**
     * «Есть свободное» запросом не выражается: это вычитание чужих броней и выделения из оценки
     * количества, а оценка зависит от очереди (PLAN H4). Пачка, требующая сверки, свободной не
     * считается — «неизвестно» это не «есть».
     */
    private suspend fun listing(query: PackageQuery, today: LocalDate): List<Package> =
        database.withTransaction {
            val found = packages.matching(query, today).map { it.toDomain() }
            if (query.filter != PackageQuery.Filter.HasFree) return@withTransaction found
            val allocations = packages.allocationsOf(found.map { it.id })
            found.filter { pkg ->
                availabilityOf(
                    pkg,
                    queue.unclosedOfPackage(pkg.id),
                    allocations.firstOrNull { it.packageId == pkg.id }
                ).freeForAnyone?.isZero == false
            }
        }

    private fun availabilityOf(
        pkg: Package,
        unclosed: List<SyncOperationStorageRow>,
        allocation: PackageAllocationRow?
    ): PackageAvailability = PackageAvailability(
        pkg = pkg,
        amount = amountOf(pkg, unclosed),
        myAllocation = allocation?.allocated ?: Quantity.zero(pkg.quantity.unitId)
    )

    /**
     * Незакрытые команды применяются к подтверждённому остатку по возрастанию номера. Операция,
     * чей исход не установлен, и команда, которую нечем прочитать после обновления приложения,
     * делают число неизвестным, а не нулевым (PLAN E1, F4).
     *
     * Ожидающая ручная сверка отсекает всё до своего среза `throughSequence`: пересчитанное число
     * эти факты уже включает, и их неустановленный исход больше не делает его неизвестным (E3).
     */
    private fun amountOf(pkg: Package, unclosed: List<SyncOperationStorageRow>): EffectiveAmount {
        val read = unclosed.map { it.operation.sequence to it.toDomain() }
        val cut = read.maxOfOrNull { (_, stored) ->
            (stored.readable()?.command as? PackageSyncCommand.Reconcile)?.throughSequence ?: -1L
        } ?: -1L
        val commands = ArrayList<PackageSyncCommand>(unclosed.size)
        val unresolved = ArrayList<Uuid>()
        for ((sequence, stored) in read) {
            if (sequence <= cut) continue
            val operation = stored.readable()
            val command = operation?.command as? PackageSyncCommand
            when {
                command == null -> unresolved += stored.id
                operation.status == SyncOperationStatus.NEEDS_RECOUNT -> unresolved += operation.id
                else -> commands += command
            }
        }
        return PackageQueueState(pkg, commands, unresolved).amount
    }

    private fun StoredSyncOperation.readable() = (this as? StoredSyncOperation.Readable)?.operation

    private companion object {

        /** Из чего складывается доступность: пачка с её сведениями и бронями, очередь, выделения. */
        val AVAILABILITY_TABLES = arrayOf(
            "packages",
            "package_details",
            "claims",
            "sync_operations",
            "courses",
            "course_sources",
            "active_package_assignments"
        )
    }
}
