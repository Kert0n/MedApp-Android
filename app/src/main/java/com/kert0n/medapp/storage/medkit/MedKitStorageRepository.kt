package com.kert0n.medapp.storage.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitProjection
import com.kert0n.medapp.domain.medkit.MedKitStatus
import com.kert0n.medapp.network.pack.PackageSnapshot
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение аптечек. Отдаёт домен, а не строки: выше по стеку о Room не знают, а сохранённые
 * сведения приходят потоком — «обновить экран после записи» руками не нужно нигде (PLAN H1).
 */
interface MedKitStorageRepository {

    /** Потоки несут проекции — величины для экрана; сущность отдаёт `find` в транзакции сценария (PLAN H1). */
    fun observeAll(): Flow<List<MedKitProjection>>

    fun observe(id: Uuid): Flow<MedKitProjection?>

    suspend fun find(id: Uuid): MedKit?

    /**
     * Когда с аптечкой последний раз сверялись — экрану состояния синхронизации (PLAN H3 №28).
     * Момент сверки принадлежит доставке, а не аптечке, и в её проекцию не входит.
     */
    fun observeSyncedAt(id: Uuid): Flow<Instant?>

    suspend fun save(medKit: MedKit, syncedAt: Instant? = null)

    /**
     * Строка аптечки уходит. Содержимое к этому моменту уже переехало или удалено — что с ним
     * делать, решает сценарий, а не хранение (PLAN E6, F5). `false` — аптечки и так нет.
     */
    suspend fun delete(id: Uuid): Boolean

    /**
     * Решение по полке принято, а сервер ещё не ответил: полка получает пометку [status] своим
     * переходом (PLAN E1, E6). Снимает её закрытие команды в очереди, поэтому `ACTIVE` сюда не
     * передают. `false` — аптечки больше нет.
     */
    suspend fun mark(medKitId: Uuid, status: MedKitStatus): Boolean

    /** Снимок трогает только число участников: остального сервер о нашей аптечке не знает. */
    suspend fun applyServerParticipants(id: Uuid, participantCount: Long, syncedAt: Instant)

}
