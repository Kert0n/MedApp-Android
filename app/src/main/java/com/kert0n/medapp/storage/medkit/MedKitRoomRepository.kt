package com.kert0n.medapp.storage.medkit

import androidx.room.withTransaction
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.toDomain
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.PackageDao
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

    override suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshotNetworkDTO>, at: Instant) =
        database.withTransaction {
            check(medKit.publication == MedKit.Publication.PUBLISHED) { "записывается опубликованная аптечка" }
            medKits.upsert(medKit.toStorageEntity(syncedAt = at))
            val words = vocabulary.snapshot()
            for (snapshot in snapshots) {
                // Снимок чужой аптечки отвергает маппер — и откатывает переключение вместе с ним.
                val resolved = snapshot.toDomain(words, medKit.ref, addedAt = at, observedAt = at)
                packages.applyServerSnapshot(resolved.pack.toStorageEntity(resolved.sync), observedAt = at)
                resolved.pack.claims?.let { packages.upsertClaims(it.toStorageEntity(snapshot.pack.id)) }
            }
        }
}
