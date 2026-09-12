package com.kert0n.medapp.feature.medkits

import com.kert0n.medapp.domain.medkit.MedKitStatus
import com.kert0n.medapp.domain.pack.PackageStatus
import com.kert0n.medapp.feature.packages.PackageRelocation
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
 * Человек убирает полку (ТЗ 4.1.1.2.3): либо выбрасывает коробки вместе с ней, либо переставляет
 * их на другую. История — записи о коробках, приёмы и движения — остаётся (PLAN E6, D3).
 *
 * **Своя полка существует только у нас**, поэтому решение и есть подтверждение: она разбирается по
 * коробкам, каждая проходит свой доменный путь — выбрасывания ([PackageRemoval]) или переезда
 * ([PackageRelocation]), — и строка полки уходит следом. Всё одной транзакцией: полки без
 * содержимого и содержимого без полки не бывает ни на миг (F5).
 *
 * **Общей полкой распоряжается сервер.** Ему уходит одна команда аптечки
 * (`DELETE /v1/med-kits/{id}?targetMedKitId=`), а до ответа **ничего не трогается**: коробки целы,
 * полка на месте, и человеку видно, что они помечены. Иначе отказ сервера уничтожил бы у нас то,
 * что у других участников живо, и вернуть это было бы нечем. Разбирает полку подтверждение —
 * эффектом очереди, там же, где закрывается операция.
 *
 * Общую полку забирают домой, на местную, по коробкам: каждая сразу у человека, серверу —
 * «унёс домой», а полка уходит у всех следом и **зависит** от них. Не вышло с коробкой — она
 * возвращается на полку, и полка остаётся: иначе сервер выбросил бы её вместе с полкой (PLAN E6).
 */
class MedKitRemoval @Inject constructor(
    private val medKits: MedKitStorageRepository,
    private val packages: PackageStorageRepository,
    private val removal: PackageRemoval,
    private val relocation: PackageRelocation,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    /** [transferTo] `null` — выбросить вместе с лекарствами; иначе перенести их туда. */
    suspend fun remove(medKitId: Uuid, transferTo: Uuid? = null): Outcome = transactions.run {
        val medKit = medKits.find(medKitId) ?: return@run Outcome.MED_KIT_GONE
        if (!medKit.status.allowsDecision) return@run Outcome.BUSY
        val target = transferTo?.let { medKits.find(it) ?: return@run Outcome.TARGET_GONE }
        if (target != null && target.id == medKit.id) return@run Outcome.TARGET_IS_THE_SAME
        val now = clock.instant()
        if (medKit.answersToServer && target != null && !target.answersToServer) {
            val withdrawals = packages.contentsOf(medKitId).filter { it.status.allowsUse }.associateWith { relocation.withdrawal(it) }
            val delete = QueuedCommand(
                Uuid.random(),
                MedKitSyncCommand.Delete(medKitId),
                dependsOn = withdrawals.values.mapTo(HashSet()) { it.id }
            )
            queue.change(medKit.ref, withdrawals.values + delete, now) {
                for (pkg in withdrawals.keys) relocation.carryHome(pkg, target.ref, now)
                medKits.mark(medKitId, MedKitStatus.REMOVING)
            }
            return@run Outcome.MARKED
        }
        if (medKit.answersToServer) {
            val delete = QueuedCommand(Uuid.random(), MedKitSyncCommand.Delete(medKitId, target?.id))
            // Коробки выбрасываемой полки выведены из оборота, переносимые — только помечены: ими
            // пользуются, пока сервер переставляет. Ждущую своего решения коробку не трогаем — её
            // отпустит её же команда (PLAN E1, E6).
            val fate = if (target == null) PackageStatus.REMOVING else PackageStatus.CHANGING
            queue.change(medKit.ref, listOf(delete), now) {
                for (pkg in packages.contentsOf(medKitId)) {
                    if (pkg.status.allowsUse) check(packages.mark(pkg.id, fate)) { "пачка прочитана этой же транзакцией" }
                }
                medKits.mark(medKitId, MedKitStatus.REMOVING)
            }
            return@run Outcome.MARKED
        }
        for (pkg in packages.contentsOf(medKitId)) {
            if (target == null) {
                removal.discard(pkg, now)
            } else {
                val moved = relocation.relocate(pkg, target, now)
                check(moved == PackageRelocation.Outcome.MOVED) { "местная коробка переезжает сразу, а не $moved" }
            }
        }
        check(medKits.delete(medKitId)) { "аптечка прочитана этой же транзакцией" }
        Outcome.REMOVED
    }

    /**
     * Чем кончилось. Случаи различает поведение экрана: убрали — уходим со списка; пометили —
     * полка остаётся на месте и ждёт согласия сервера; аптечки уже нет — закрываем молча; некуда
     * переносить — просим выбрать другую; та же — говорим об этом; полка уже ждёт другого решения —
     * ждём его ответа (PLAN E1, E6).
     */
    enum class Outcome {
        REMOVED,
        MARKED,
        MED_KIT_GONE,
        BUSY,
        TARGET_GONE,
        TARGET_IS_THE_SAME
    }
}
