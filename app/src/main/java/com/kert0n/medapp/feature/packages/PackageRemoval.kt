package com.kert0n.medapp.feature.packages

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек выбросил коробку (ТЗ 4.1.1.3.5). Коробки больше нет, а история — приёмы и движения —
 * держится за запись о ней и остаётся (PLAN D3, D6, D7).
 *
 * **Решение и подтверждение — разные моменты, и разъезжаются они по границе публикации.** Своя
 * полка существует только у нас: решение и есть подтверждение, коробка кончается сразу. Общую
 * полку видят другие люди, и выбросить с неё молча нельзя — сосед мог отложить коробку себе. Туда
 * уходит команда `Delete` со своим предусловием, а до ответа коробка **цела и видна**: человеку
 * показано, что она помечена (PLAN E1). Конец ей приносит подтверждение сервера, и тогда же
 * лечение теряет её источником.
 */
class PackageRemoval @Inject constructor(
    private val packages: PackageStorageRepository,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun remove(packageId: Uuid): Outcome = transactions.run {
        val pkg = packages.find(packageId) ?: return@run Outcome.GONE
        val now = clock.instant()
        if (pkg.medKit.answersToServer) {
            val delete = QueuedCommand(Uuid.random(), PackageSyncCommand.Delete(pkg.id))
            queue.change(pkg.medKit, listOf(delete), now) { true }
            Outcome.MARKED
        } else {
            discard(pkg, now)
            Outcome.REMOVED
        }
    }

    /**
     * Шаг внутри чужой транзакции — коробка уходит по решению человека: в историю идёт утилизация
     * всего остатка, потому что выброшенное никем не принято и без следа пропало бы из учёта (H6).
     */
    internal suspend fun discard(pkg: Package, at: Instant) {
        packages.end(pkg.thrownOut(Uuid.random(), at), at)
    }

    /**
     * Шаг внутри чужой транзакции — доступ к коробке утрачен, из аптечки вышли: последний виденный
     * остаток уходит из учёта записью в историю (PLAN D7, E6).
     */
    internal suspend fun lose(pkg: Package, at: Instant) {
        packages.end(pkg.lost(Uuid.random(), at), at)
    }

    /**
     * Чем кончилось. Убрали — экран уходит с карточки; пометили — карточка остаётся и говорит, что
     * коробка ждёт ответа полки; коробки и так нет — закрывает молча.
     */
    enum class Outcome { REMOVED, MARKED, GONE }
}
