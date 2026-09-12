package com.kert0n.medapp.feature.medkits

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек выбрасывает аптечку (ТЗ 4.1.1.2.3): либо вместе с лекарствами, либо перенеся их в
 * другую. Оба пути — одна транзакция: аптечки без содержимого и содержимого без аптечки не
 * бывает ни на миг (PLAN E6, F5).
 *
 * Переезжает **всё** содержимое, и живое, и архивное: уносят место, а не каждую коробку отдельно.
 * Удаление уносит пачки со всеми их частями — сведениями, бронями, историей остатка и связями с
 * курсами (D3). История лечения это переживает: она держится на записи эпизода и на приёмах, а
 * ссылка приёма на исчезнувшую пачку пустеет (D6).
 *
 * Общая аптечка местным решением не удаляется: она существует у других людей, и убрать её можно
 * только сервером (PLAN C3, E5) — это приедет вместе с публикацией.
 */
class MedKitRemoval @Inject constructor(
    private val medKits: MedKitStorageRepository,
    private val packages: PackageStorageRepository,
    private val transactions: Transactions
) {

    /** [transferTo] `null` — выбросить вместе с лекарствами; иначе перенести их туда. */
    suspend fun remove(medKitId: Uuid, transferTo: Uuid? = null): Outcome = transactions.run {
        val medKit = medKits.find(medKitId) ?: return@run Outcome.MED_KIT_GONE
        if (medKit.publication != MedKit.Publication.LOCAL) return@run Outcome.NEEDS_NETWORK
        if (transferTo != null) {
            val target = medKits.find(transferTo) ?: return@run Outcome.TARGET_GONE
            if (target.id == medKit.id) return@run Outcome.TARGET_IS_THE_SAME
            packages.moveContents(from = medKitId, to = target.id)
        } else {
            packages.deleteContentsOf(medKitId)
        }
        if (medKits.delete(medKitId)) Outcome.REMOVED else Outcome.MED_KIT_GONE
    }

    /**
     * Чем кончилось. Случаи различает поведение экрана: убрали — уходим со списка; аптечки уже
     * нет — закрываем молча; некуда переносить — просим выбрать другую; общая — объясняем, что
     * нужна связь.
     */
    enum class Outcome {
        REMOVED,
        MED_KIT_GONE,
        TARGET_GONE,
        TARGET_IS_THE_SAME,
        NEEDS_NETWORK
    }
}
