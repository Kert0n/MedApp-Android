package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitProjection
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageProjection
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.storage.course.CourseReallocation
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageAdjustment
import com.kert0n.medapp.storage.pack.PackageQuery
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.storage.pack.SnapshotApplied
import com.kert0n.medapp.storage.stock.StockMovementStorageRepository
import com.kert0n.medapp.storage.value.VocabularyStorageRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Хранение в памяти для проверок представления и сценариев: базы там нет вовсе, а поведение
 * репозитория нужно настоящее — записанное видно следующим чтением, и переход применяется к
 * тому, что лежит внутри, а не к тому, что подставил тест (PLAN F5).
 *
 * Порядок списка здесь не воспроизводится: он принадлежит запросу базы и проверяется на ней
 * (`PackageQueryDaoTest`). Подделка отвечает за состав, а не за сортировку.
 */
class FakePackages(vararg packs: Package) : PackageStorageRepository {

    private val stored = LinkedHashMap<Uuid, Package>()

    /** Что записано: тест спрашивает хранилище, а не следит за вызовами. */
    val packages: List<Package> get() = stored.values.toList()

    private val changes = MutableStateFlow(0)

    init {
        packs.forEach { stored[it.id] = it }
    }

    override fun observe(id: Uuid): Flow<PackageProjection?> =
        changes.map { stored[id]?.projected() }

    override suspend fun find(id: Uuid): Package? = stored[id]

    override fun list(query: PackageQuery, today: LocalDate): Flow<List<PackageProjection>> =
        changes.map { matching(query).map { it.projected() } }

    override suspend fun add(pkg: Package, sync: PackageSyncState) {
        stored[pkg.id] = pkg
        changes.value++
    }

    override suspend fun describe(packageId: Uuid, facts: PackageFacts): Boolean =
        change(packageId) { it.describe(facts) }

    override suspend fun loseAccess(packageId: Uuid): Boolean = change(packageId) { it.loseAccess() }

    override suspend fun adjust(
        adjustment: PackageAdjustment,
        reallocation: CourseReallocation?,
        at: Instant
    ): Boolean {
        val stored = stored[adjustment.packageId] ?: return false
        val applied = adjustment.applyTo(stored, at)
        this.stored[applied.pack.id] = applied.pack
        // След есть не у всякого перехода: перенос остаток не меняет и записи не оставляет (D7).
        applied.movement?.let { movements += it }
        changes.value++
        return true
    }

    /** След, оставленный переходами: пересчёт и утилизация без него — потерянное лекарство. */
    val movements = mutableListOf<StockMovement>()

    override suspend fun delete(packageId: Uuid): Boolean {
        val removed = stored.remove(packageId) != null
        if (removed) {
            // Движения — части пачки: их уносит та же операция, что и её саму (PLAN D3).
            movements.removeAll { it.pkg.id == packageId }
            changes.value++
        }
        return removed
    }

    override suspend fun moveContents(from: Uuid, to: Uuid): Int {
        val moved = stored.values.filter { it.medKit.id == from }
        moved.forEach { stored[it.id] = it.inMedKit(to) }
        if (moved.isNotEmpty()) changes.value++
        return moved.size
    }

    override suspend fun deleteContentsOf(medKitId: Uuid): Int {
        val gone = stored.values.filter { it.medKit.id == medKitId }.map { it.id }
        gone.forEach { delete(it) }
        return gone.size
    }

    override suspend fun applySnapshot(snapshot: PackageSnapshot, observedAt: Instant): SnapshotApplied =
        SnapshotApplied(pack = false, claims = false)

    override fun observeSyncState(id: Uuid): Flow<PackageSyncState?> = emptyFlow()

    override suspend fun saveClaims(packageId: Uuid, claims: Claims?) = Unit

    private fun matching(query: PackageQuery): List<Package> = stored.values.filter { pkg ->
        (query.includeArchived || pkg.lifecycle == Package.Lifecycle.ACTIVE) &&
            (query.medKitId == null || pkg.medKit.id == query.medKitId) &&
            (query.searchText.isEmpty() || pkg.name.lowercase().contains(query.searchText))
    }

    /**
     * Пачка в другом месте: переезд содержимого — правка строки, а не переход пачки, поэтому
     * архивная переезжает вместе с живой (PLAN E6).
     */
    private fun Package.inMedKit(medKitId: Uuid): Package = Package(
        id = id,
        medKit = MedKitRef(medKitId, medKit.publication),
        facts = facts,
        quantity = quantity,
        addedAt = addedAt,
        templateId = templateId,
        claims = claims,
        lifecycle = lifecycle,
        access = access
    )

    private fun change(packageId: Uuid, transition: (Package) -> Package): Boolean {
        val stored = stored[packageId] ?: return false
        this.stored[packageId] = transition(stored)
        changes.value++
        return true
    }
}

/** Аптечки в памяти: содержимое считает тот, кто его видит, поэтому проекции идут пустыми. */
class FakeMedKits(vararg kits: MedKit) : MedKitStorageRepository {

    private val stored = LinkedHashMap<Uuid, MedKit>()

    private val changes = MutableStateFlow(0)

    val medKits: List<MedKit> get() = stored.values.toList()

    init {
        kits.forEach { stored[it.id] = it }
    }

    override fun observeAll(today: LocalDate): Flow<List<MedKitProjection>> =
        changes.map { stored.values.map { it.projection() } }

    override fun observe(id: Uuid): Flow<MedKitProjection?> = changes.map { stored[id]?.projection() }

    override suspend fun find(id: Uuid): MedKit? = stored[id]

    override fun observeSyncedAt(id: Uuid): Flow<Instant?> = emptyFlow()

    override suspend fun save(medKit: MedKit, syncedAt: Instant?) {
        stored[medKit.id] = medKit
        changes.value++
    }

    override suspend fun describe(id: Uuid, name: String, location: String?): Boolean {
        val stored = stored[id] ?: return false
        this.stored[id] = stored.describe(name, location)
        changes.value++
        return true
    }

    override suspend fun delete(id: Uuid): Boolean {
        val removed = stored.remove(id) != null
        if (removed) changes.value++
        return removed
    }

    /** «Аптечку удалили, пока экран был открыт» — то же самое, что и удаление сценарием. */
    fun forget(id: Uuid) {
        stored.remove(id)
        changes.value++
    }

    override suspend fun applyServerParticipants(id: Uuid, participantCount: Long, syncedAt: Instant) = Unit

    override suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant) = Unit
}

/** История движений в памяти: записи только добавляются (PLAN D7). */
class FakeMovements : StockMovementStorageRepository {

    val recorded = mutableListOf<StockMovement>()

    override suspend fun record(movement: StockMovement) {
        recorded += movement
    }

    override fun observeOfPackage(packageId: Uuid): Flow<List<StockMovement>> =
        MutableStateFlow(recorded.filter { it.pkg.id == packageId })

    override suspend fun ofPackage(packageId: Uuid): List<StockMovement> =
        recorded.filter { it.pkg.id == packageId }

    override suspend fun observedBetween(from: Instant, until: Instant): List<StockMovement> =
        recorded.filter { it.observedAt >= from && it.observedAt < until }
}

/** Словарь, известный тестам: тот же снимок, что и у фикстур домена. */
class FakeVocabulary(private val snapshot: Vocabulary = VOCABULARY) : VocabularyStorageRepository {

    override suspend fun snapshot(): Vocabulary = snapshot

    override suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>) = Unit

    override fun observeUnits(): Flow<List<QuantityUnit>> = MutableStateFlow(listOf(TABLETS, MILLILITRES))

    override fun observeForms(): Flow<List<DosageForm>> =
        MutableStateFlow(listOf(TABLET_FORM, CAPSULE_FORM))
}

/**
 * «Одна транзакция» без базы: тело выполняется как есть. Подделка не обещает отката — что
 * половина записи невозможна, проверяется на настоящей базе (PLAN F5).
 */
object DirectTransactions : Transactions {

    override suspend fun <T> run(block: suspend () -> T): T = block()
}
