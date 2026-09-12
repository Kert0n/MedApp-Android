package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Quantity
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

    /** Перенос меняет место, а не остаток: в истории у него два конца (PLAN D7). */
    suspend fun moveTo(packageId: Uuid, target: MedKitRef, note: String? = null): Boolean =
        packages.adjust(
            PackageAdjustment.Transfer(packageId, target, movementId = Uuid.random(), note = note),
            at = clock.instant()
        )
}
