package com.kert0n.medapp.feature.medkits

import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек выходит из общей аптечки (ТЗ 4.1.1.11.3, PLAN E6): полка остаётся остальным, а у нас
 * пропадает доступ к её коробкам.
 *
 * Серверу уходит `Leave`, и до его ответа **ничего не трогается**: коробки целы и видны
 * помеченными. Выход — наше участие, и сервер его не оспаривает, но узнать о нём мы можем только
 * от него; до тех пор человек ещё в аптечке (E3). Подтверждение приносит остальное эффектом
 * очереди: последний виденный остаток каждой коробки уходит в историю утратой доступа, курс теряет
 * источники с этой полки — и только их, — строки уходят. Курс и его история остаются.
 *
 * Из местной аптечки не выходят — её выбрасывают ([MedKitRemoval]): она существует только у нас.
 */
class MedKitLeaving @Inject constructor(
    private val medKits: MedKitStorageRepository,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun leave(medKitId: Uuid): Outcome = transactions.run {
        val medKit = medKits.find(medKitId) ?: return@run Outcome.MED_KIT_GONE
        if (!medKit.answersToServer) return@run Outcome.NOT_SHARED
        val leave = QueuedCommand(Uuid.random(), MedKitSyncCommand.Leave(medKitId))
        queue.change(medKit.ref, listOf(leave), clock.instant()) { true }
        Outcome.MARKED
    }

    /**
     * Чем кончилось. Пометили — полка остаётся на месте и ждёт ответа сервера; аптечки уже нет —
     * закрываем молча; местная — выходить неоткуда.
     */
    enum class Outcome { MARKED, MED_KIT_GONE, NOT_SHARED }
}
