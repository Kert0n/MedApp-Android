package com.kert0n.medapp.storage.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedKitRoomRepository @Inject constructor(
    private val medKits: MedKitDao
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
}
