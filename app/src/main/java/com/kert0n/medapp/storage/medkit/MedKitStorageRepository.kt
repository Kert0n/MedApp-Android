package com.kert0n.medapp.storage.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.network.pack.PackageSnapshot
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

    /**
     * Момент передачи ответственности серверу (PLAN E5): аптечка становится опубликованной, а
     * ответы на создание её пачек — первым подтверждённым остатком и версиями — одной транзакцией.
     * До неё истина — устройство и очереди нет; после — сервер, и изменения идут командами.
     */
    suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant)
}
