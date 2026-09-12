package com.kert0n.medapp.feature.medkits

import com.kert0n.medapp.feature.packages.PackageRemoval
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек выходит из общей аптечки (ТЗ 4.1.1.11.3, PLAN E6): полка остаётся остальным, а у нас
 * пропадает доступ к её коробкам. Коробки целы, но не у нас: последний виденный остаток каждой
 * уходит в историю утратой доступа, курс теряет источники с этой полки — и только их, — строки
 * уходят; курс и его история остаются. Серверу — `Leave`, брони он снимает сам по участию. Одна
 * транзакция.
 *
 * Из местной аптечки не выходят — её выбрасывают ([MedKitRemoval]): она существует только у нас.
 */
class MedKitLeaving @Inject constructor(
    private val medKits: MedKitStorageRepository,
    private val packages: PackageStorageRepository,
    private val removal: PackageRemoval,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun leave(medKitId: Uuid): Outcome = transactions.run {
        val medKit = medKits.find(medKitId) ?: return@run Outcome.MED_KIT_GONE
        if (!medKit.answersToServer) return@run Outcome.NOT_SHARED
        val now = clock.instant()
        for (pkg in packages.contentsOf(medKitId)) removal.lose(pkg, now)
        queue.change(medKit.ref, listOf(QueuedCommand(Uuid.random(), MedKitSyncCommand.Leave(medKitId))), now) { true }
        check(medKits.delete(medKitId)) { "аптечка прочитана этой же транзакцией" }
        Outcome.LEFT
    }

    /** Вышли — уходим со списка; аптечки уже нет — закрываем молча; местная — выходить неоткуда. */
    enum class Outcome { LEFT, MED_KIT_GONE, NOT_SHARED }
}
