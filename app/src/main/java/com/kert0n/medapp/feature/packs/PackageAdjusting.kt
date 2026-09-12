package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageAdjustment
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Пересчёт, утилизация и перенос — действия человека, меняющие остаток или место пачки помимо
 * приёма (PLAN D7). Каждое ложится вместе со своим следом в истории одной транзакцией, и переход
 * применяется к тому, что лежит в базе, а не к тому, что экран прочитал когда-то раньше (F5):
 * «было 20, стало 17» в истории — настоящее «было».
 *
 * Тождество записи движения и момент придумываются здесь, а не на экране: экран называет
 * действие, а не сочиняет учётную запись.
 *
 * `false` — пачки больше нет: писать переход некуда.
 */
class PackageAdjusting @Inject constructor(
    private val packages: PackageStorageRepository,
    private val medKits: MedKitStorageRepository,
    private val transactions: Transactions,
    private val clock: Clock
) {

    /** «Пересчитал и увидел столько» — замена значения, а не разница (PLAN E1). */
    suspend fun recount(packageId: Uuid, actual: Quantity, note: String? = null): Boolean =
        packages.adjust(
            PackageAdjustment.Recount(packageId, actual, movementId = Uuid.random(), note = note),
            at = clock.instant()
        )

    /** Выбросили названное количество по названной причине; ушедшая в ноль пачка архивируется. */
    suspend fun dispose(
        packageId: Uuid,
        amount: Quantity,
        reason: StockMovement.Disposal.Reason,
        note: String? = null
    ): Boolean = packages.adjust(
        PackageAdjustment.Disposal(packageId, amount, reason, movementId = Uuid.random(), note = note),
        at = clock.instant()
    )

    /**
     * Перенос меняет место, а не остаток, и следа в истории не оставляет: истрачено ничего не
     * было, а где коробка лежит, знает сама пачка (PLAN D7). Аптечка назначения читается в той же
     * транзакции, что и сам перенос: удалённая между выбором и подтверждением — это «переносить
     * некуда», а не пачка в несуществующем месте.
     *
     * Перенос между местными аптечками — целиком дело устройства (PLAN E6); перенос в общую
     * требует связи и появится вместе с публикацией.
     */
    suspend fun moveTo(packageId: Uuid, targetId: Uuid, note: String? = null): Boolean =
        transactions.run {
            val target = medKits.find(targetId) ?: return@run false
            packages.adjust(
                PackageAdjustment.Transfer(packageId, target.ref, note = note),
                at = clock.instant()
            )
        }
}
