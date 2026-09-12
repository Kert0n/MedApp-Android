package com.kert0n.medapp.storage.medkit

import androidx.room.withTransaction
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitProjection
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.queue.medkit.PublicationStorage
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.applySnapshot
import com.kert0n.medapp.storage.pack.toStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedKitRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val medKits: MedKitDao,
    private val packages: PackageDao,
    private val vocabulary: VocabularyDao
) : MedKitStorageRepository, PublicationStorage {

    override fun observeAll(): Flow<List<MedKitProjection>> =
        medKits.observeAll().map { rows -> rows.map { it.toDomain().projection() } }

    override fun observe(id: Uuid): Flow<MedKitProjection?> =
        medKits.observe(id).map { it?.toDomain()?.projection() }

    override suspend fun find(id: Uuid): MedKit? = medKits.find(id)?.toDomain()

    override suspend fun medKit(id: Uuid): MedKit? = find(id)

    override suspend fun contentsOf(medKitId: Uuid): List<Package> = database.withTransaction {
        val words = vocabulary.snapshot()
        packages.ofMedKit(medKitId).map { it.toDomain(words) }
    }

    override fun observeSyncedAt(id: Uuid): Flow<Instant?> = medKits.observe(id).map { it?.syncedAt }

    override suspend fun save(medKit: MedKit, syncedAt: Instant?) =
        medKits.upsert(medKit.toStorageEntity(syncedAt))

    override suspend fun delete(id: Uuid): Boolean = medKits.delete(id) > 0

    override suspend fun applyServerParticipants(
        id: Uuid,
        participantCount: Long,
        syncedAt: Instant
    ) = medKits.applyServerParticipants(id, participantCount, syncedAt)

    override suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant): Boolean =
        database.withTransaction {
            check(medKit.answersToServer) { "записывается опубликованная аптечка" }
            // Снимок чужой аптечки сюда не ложится — и откатывает переключение вместе с собой.
            require(snapshots.all { it.pack.medKit.id == medKit.id }) { "публикуются снимки этой аптечки" }
            // Между чтением содержимого и этой записью человек продолжал жить: пачка, которая
            // изменилась или появилась, серверу не досталась, и старый снимок лёг бы поверх
            // нового местного (E5). Тогда переключения нет.
            val words = vocabulary.snapshot()
            val current = packages.ofMedKit(medKit.id).map { it.toDomain(words) }.associateBy { it.id }
            val unchanged = current.size == snapshots.size && snapshots.all { snapshot ->
                val stored = current[snapshot.pack.id] ?: return@all false
                stored.quantity == snapshot.pack.quantity && stored.facts.shared == snapshot.pack.facts.shared
            }
            if (!unchanged) return@withTransaction false
            medKits.upsert(medKit.toStorageEntity(syncedAt = at))
            for (snapshot in snapshots) packages.applySnapshot(snapshot, observedAt = at)
            true
        }
}
