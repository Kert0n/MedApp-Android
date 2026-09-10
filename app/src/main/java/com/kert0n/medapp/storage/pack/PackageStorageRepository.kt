package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAvailability
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.storage.server.QueuedCommand
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение упаковок. Отдаёт домен, а не строки, и собирает пачку из трёх её таблиц —
 * серверной части, личных сведений и картины броней (PLAN F1, H1).
 *
 * Оценку количества считает очередь: репозиторий берёт незакрытые команды по номеру и сворачивает
 * их существующим `PackageQueueState`, а домену отдаёт готовый `EffectiveAmount` (PLAN E1).
 */
interface PackageStorageRepository {

    fun observe(id: Uuid): Flow<Package?>

    suspend fun find(id: Uuid): Package?

    /**
     * Доступность одной пачки: оценка количества, чужие брони и занятое активным курсом.
     * Своего выделения у пачки вне курса нет, поэтому оно берётся из назначения (PLAN D4).
     */
    fun observeAvailability(id: Uuid): Flow<PackageAvailability?>

    /** Список экрана: `today` приходит аргументом, потому что база системных часов не читает. */
    fun list(query: PackageQuery, today: LocalDate): Flow<List<Package>>

    suspend fun save(pkg: Package, sync: PackageSyncState = PackageSyncState(pkg.id))

    /** Снимок переписывает серверную часть целиком и не касается личных сведений (PLAN E4). */
    suspend fun applyServerSnapshot(pkg: Package, sync: PackageSyncState, observedAt: Instant)

    /** `null` снимает картину броней: аптечка не опубликована либо доступ утрачен. */
    suspend fun saveClaims(packageId: Uuid, claims: Claims?)

    /**
     * Пересчёт, утилизация и перенос: движение и новое состояние пачки ложатся одной транзакцией
     * вместе с пересчитанными выделениями [course] и исходящей командой [command] (PLAN F5).
     *
     * Переход применяется к нынешнему состоянию пачки, прочитанному в той же транзакции, поэтому
     * «было» в истории — настоящее «было». Обвязка синхронизации при этом не трогается: версии и
     * время сверки принадлежат снимку сервера, а не действию человека (PLAN E4).
     *
     * Откат не оставляет ни движения без остатка, ни остатка без следа в истории. `false` —
     * пачки больше нет: писать переход некуда.
     */
    suspend fun adjust(
        adjustment: PackageAdjustment,
        course: Course? = null,
        command: QueuedCommand? = null,
        at: Instant
    ): Boolean
}
