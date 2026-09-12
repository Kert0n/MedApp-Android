package com.kert0n.medapp.storage.medkit

import androidx.room.withTransaction
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitContents
import com.kert0n.medapp.domain.medkit.MedKitProjection
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.applySnapshot
import com.kert0n.medapp.storage.pack.toStorageEntity
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedKitRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val medKits: MedKitDao,
    private val packages: PackageDao
) : MedKitStorageRepository {

    /**
     * Аптечки и их содержимое читаются одной транзакцией: `combine` независимых потоков дал бы
     * список, которого в базе не было, — свежие аптечки со старым счётом пачек.
     */
    override fun observeAll(today: LocalDate): Flow<List<MedKitProjection>> =
        database.invalidationTracker.createFlow("med_kits", "packages", "package_details")
            .map { listing(today) }

    private suspend fun listing(today: LocalDate): List<MedKitProjection> = database.withTransaction {
        val contents = medKits.contents(today).associateBy { it.medKitId }
        medKits.all().map { row ->
            val inside = contents[row.id]
            row.toDomain().projection(
                MedKitContents(packages = inside?.packages ?: 0, expired = inside?.expired ?: 0)
            )
        }
    }

    override fun observe(id: Uuid): Flow<MedKitProjection?> =
        medKits.observe(id).map { it?.toDomain()?.projection() }

    override suspend fun find(id: Uuid): MedKit? = medKits.find(id)?.toDomain()

    override fun observeSyncedAt(id: Uuid): Flow<Instant?> = medKits.observe(id).map { it?.syncedAt }

    override suspend fun save(medKit: MedKit, syncedAt: Instant?) =
        medKits.upsert(medKit.toStorageEntity(syncedAt))

    override suspend fun delete(id: Uuid): Boolean = medKits.delete(id) > 0

    override suspend fun describe(id: Uuid, name: String, location: String?): Boolean =
        database.withTransaction {
            val stored = medKits.find(id) ?: return@withTransaction false
            // Момент сверки принадлежит снимку сервера, а не правке человека (PLAN E4).
            medKits.upsert(stored.toDomain().describe(name, location).toStorageEntity(stored.syncedAt))
            true
        }

    override suspend fun applyServerParticipants(
        id: Uuid,
        participantCount: Long,
        syncedAt: Instant
    ) = medKits.applyServerParticipants(id, participantCount, syncedAt)

    override suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant) =
        database.withTransaction {
            check(medKit.publication == MedKit.Publication.PUBLISHED) { "записывается опубликованная аптечка" }
            // Снимок чужой аптечки сюда не ложится — и откатывает переключение вместе с собой.
            require(snapshots.all { it.pack.medKit.id == medKit.id }) { "публикуются снимки этой аптечки" }
            medKits.upsert(medKit.toStorageEntity(syncedAt = at))
            for (snapshot in snapshots) packages.applySnapshot(snapshot, observedAt = at)
        }
}
