package com.kert0n.medapp.storage.pack

import androidx.room.withTransaction
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAvailability
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.pack.PackageQueueState
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.SyncOperationStatus
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.server.SyncOperationStorageRow
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    override fun observeAvailability(id: Uuid): Flow<PackageAvailability?> = combine(
        packages.observe(id),
        queue.observeUnclosedOfPackage(id),
        packages.observeAllocations(listOf(id))
    ) { row, unclosed, allocations ->
        row?.let { availabilityOf(it.toDomain(), unclosed, allocations.firstOrNull()) }
    }

    override fun list(query: PackageQuery, today: LocalDate): Flow<List<Package>> {
        val found = packages.matching(query, today).map { rows -> rows.map { it.toDomain() } }
        if (query.filter != PackageQuery.Filter.HasFree) return found
        return found.flatMapLatest { list -> withFreeOnly(list) }
    }

    override suspend fun save(pkg: Package, sync: PackageSyncState) =
        packages.save(pkg.toStorageEntity(sync), pkg.toDetailsStorageEntity())

    override suspend fun applyServerSnapshot(
        pkg: Package,
        sync: PackageSyncState,
        observedAt: Instant
    ) = packages.applyServerSnapshot(pkg.toStorageEntity(sync), observedAt)

    override suspend fun saveClaims(packageId: Uuid, claims: Claims?) {
        if (claims == null) packages.deleteClaims(packageId)
        else packages.upsertClaims(claims.toStorageEntity(packageId))
    }

    override suspend fun adjust(adjustment: PackageAdjustment, at: Instant) =
        database.withTransaction {
            val pack = adjustment.pack
            packages.save(pack.toStorageEntity(adjustment.sync), pack.toDetailsStorageEntity())
            movements.insert(adjustment.movement.toMovementStorageEntity())
            adjustment.course?.let {
                courses.upsertCourse(it.toCourseStorageEntity())
                courses.deleteSourcesOf(it.id)
                courses.insertSources(it.medicine.toSourceStorageEntities(it.id))
            }
            adjustment.command?.let {
                queue.enqueue(
                    id = it.id,
                    command = it.command,
                    createdAt = at,
                    groupId = it.groupId,
                    dependsOn = it.dependsOn
                )
            }
            Unit
        }

    /**
     * «Есть свободное» запросом не выражается: это вычитание чужих броней и выделения из оценки
     * количества, а оценка зависит от очереди (PLAN H4). Пачка, требующая сверки, свободной не
     * считается — «неизвестно» это не «есть».
     */
    private fun withFreeOnly(found: List<Package>): Flow<List<Package>> {
        if (found.isEmpty()) return flowOf(emptyList())
        val ids = found.map { it.id }
        return combine(
            combine(ids.map { queue.observeUnclosedOfPackage(it) }) { it.toList() },
            packages.observeAllocations(ids)
        ) { unclosedPerPackage, allocations ->
            found.filterIndexed { index, pkg ->
                val availability = availabilityOf(
                    pkg,
                    unclosedPerPackage[index],
                    allocations.firstOrNull { it.packageId == pkg.id }
                )
                availability.freeForAnyone?.isZero == false
            }
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
     */
    private fun amountOf(pkg: Package, unclosed: List<SyncOperationStorageRow>): EffectiveAmount {
        val commands = ArrayList<PackageSyncCommand>(unclosed.size)
        val unresolved = ArrayList<Uuid>()
        for (row in unclosed) {
            val operation = row.toDomainOrNull()
            val command = operation?.command as? PackageSyncCommand
            when {
                command == null -> unresolved += row.operation.id
                operation.status == SyncOperationStatus.NEEDS_RECOUNT -> unresolved += operation.id
                else -> commands += command
            }
        }
        return PackageQueueState(pkg, commands, unresolved).amount
    }
}
