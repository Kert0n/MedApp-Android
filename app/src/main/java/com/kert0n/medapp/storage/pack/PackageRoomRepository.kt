package com.kert0n.medapp.storage.pack

import androidx.room.withTransaction
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAvailability
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageProjection
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.queue.PackageQueueState
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.CourseReallocation
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao
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
    private val queue: SyncOperationDao,
    private val vocabulary: VocabularyDao
) : PackageStorageRepository {

    /**
     * Снимок словаря читается после строки, а не вместе с ней, и это безопасно: словарь только
     * растёт, а единица ложится в базу не позже строки, которая её называет.
     */
    override fun observe(id: Uuid): Flow<PackageProjection?> =
        onChange { projectionOf(id) }

    override suspend fun find(id: Uuid): Package? =
        packages.find(id)?.toDomain(vocabulary.snapshot())

    override fun list(query: PackageQuery, today: LocalDate): Flow<List<PackageProjection>> =
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
            val changed = transition(stored.toDomain(vocabulary.snapshot()))
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
    ): Boolean = packages.applyServerSnapshot(pkg.toStorageEntity(sync), observedAt)

    override suspend fun saveClaims(packageId: Uuid, claims: Claims?) {
        if (claims == null) packages.deleteClaims(packageId)
        else packages.upsertClaims(claims.toStorageEntity(packageId))
    }

    override suspend fun adjust(
        adjustment: PackageAdjustment,
        reallocation: CourseReallocation?,
        at: Instant
    ): Boolean = database.withTransaction {
        val stored = packages.find(adjustment.packageId) ?: return@withTransaction false
        val applied = adjustment.applyTo(stored.toDomain(vocabulary.snapshot()), at)
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

    private suspend fun projectionOf(id: Uuid): PackageProjection? = database.withTransaction {
        val words = vocabulary.snapshot()
        val pkg = packages.find(id)?.toDomain(words) ?: return@withTransaction null
        projectionOf(pkg, packages.allocationsOf(listOf(id)).firstOrNull(), words)
    }

    /**
     * Список — готовые проекции одним чтением. «Есть свободное» запросом не выражается: это
     * вычитание чужих броней и выделения из оценки количества, а оценка зависит от очереди
     * (PLAN H4).
     */
    private suspend fun listing(query: PackageQuery, today: LocalDate): List<PackageProjection> =
        database.withTransaction {
            val words = vocabulary.snapshot()
            val found = packages.matching(query, today).map { it.toDomain(words) }
            val allocations = packages.allocationsOf(found.map { it.id })
            val projected = found.map { pkg -> projectionOf(pkg, allocations.firstOrNull { it.packageId == pkg.id }, words) }
            if (query.filter != PackageQuery.Filter.HasFree) projected
            else projected.filter { !it.availability.freeForAnyone.isZero }
        }

    /**
     * Проекция пачки: оценка количества — незакрытые команды поверх подтверждённого остатка по
     * возрастанию номера; команда, которую нечем прочитать после обновления приложения, в число
     * не входит и названа среди нечитаемых отдельно (PLAN E1, F4). Выделение — из назначения
     * активному курсу (PLAN D4).
     */
    private suspend fun projectionOf(pkg: Package, allocation: PackageAllocationRow?, words: Vocabulary): PackageProjection {
        val commands = queue.unclosedOfPackage(pkg.id).mapNotNull {
            (it.toDomain(words) as? StoredSyncOperation.Readable)?.operation?.command as? PackageSyncCommand
        }
        val state = PackageQueueState(pkg, commands)
        val availability = PackageAvailability(
            pkg = pkg,
            effective = state.amount,
            myAllocation = allocation?.allocated(words, pkg.quantity.unit) ?: Quantity.zero(pkg.quantity.unit)
        )
        return pkg.projection(availability, state.hasUnconfirmedChanges)
    }

    private companion object {

        /** Из чего складывается доступность: пачка с её сведениями и бронями, очередь, выделения. */
        val AVAILABILITY_TABLES = arrayOf(
            "packages",
            "package_details",
            "claims",
            "sync_operations",
            "courses",
            "course_sources",
            "active_package_assignments",
            "quantity_units",
            "form_types"
        )
    }
}
