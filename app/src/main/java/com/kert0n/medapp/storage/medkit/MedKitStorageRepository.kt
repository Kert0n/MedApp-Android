package com.kert0n.medapp.storage.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение аптечек. Отдаёт домен, а не строки: выше по стеку о Room не знают, а сохранённые
 * сведения приходят потоком — «обновить экран после записи» руками не нужно нигде (PLAN H1).
 */
interface MedKitStorageRepository {

    fun observeAll(): Flow<List<MedKit>>

    fun observe(id: Uuid): Flow<MedKit?>

    suspend fun find(id: Uuid): MedKit?

    suspend fun save(medKit: MedKit, syncedAt: Instant? = null)

    /** Снимок трогает только число участников: остального сервер о нашей аптечке не знает. */
    suspend fun applyServerParticipants(id: Uuid, participantCount: Long, syncedAt: Instant)
}
