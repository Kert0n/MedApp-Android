package com.kert0n.medapp.storage.medkit

import androidx.room.withTransaction
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.toStorageEntity
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedKitRoomRepository @Inject constructor(
    private val database: MedAppDatabase,
    private val medKits: MedKitDao,
    private val packages: PackageDao
) : MedKitStorageRepository {

    override fun observeAll(): Flow<List<MedKit>> =
        medKits.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: Uuid): Flow<MedKit?> =
        medKits.observe(id).map { it?.toDomain() }

    override suspend fun find(id: Uuid): MedKit? = medKits.find(id)?.toDomain()

    override suspend fun save(medKit: MedKit, syncedAt: Instant?) =
        medKits.upsert(medKit.toStorageEntity(syncedAt))

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
            for (snapshot in snapshots) {
                packages.applyServerSnapshot(snapshot.pack.toStorageEntity(snapshot.sync), observedAt = at)
                snapshot.pack.claims?.let { packages.upsertClaims(it.toStorageEntity(snapshot.pack.id)) }
            }
        }
}
