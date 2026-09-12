package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.storage.stock.StockMovementStorageRepository
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Целое действие «принёс из аптеки»: пачка и её приход записываются одной транзакцией (PLAN F5).
 * Порознь их не бывает — пачка без прихода это остаток, взявшийся ниоткуда, и история не сходится
 * с первого же дня (PLAN D7).
 *
 * Аптечка читается в той же транзакции: удалённая между открытием формы и сохранением аптечка —
 * это «писать некуда», а не «завести пачку неизвестно где».
 *
 * Очередь тут не при чём: локальная аптечка на сервере не существует, а публикация отправляет её
 * пачки сама (PLAN E5).
 */
class PackageCreation @Inject constructor(
    private val packages: PackageStorageRepository,
    private val medKits: MedKitStorageRepository,
    private val movements: StockMovementStorageRepository,
    private val transactions: Transactions,
    private val clock: Clock
) {

    /** Заводит пачку и её приход; `null` — аптечки больше нет, и записывать некуда. */
    suspend fun create(medKitId: Uuid, facts: PackageFacts, amount: Quantity): Uuid? =
        transactions.run {
            val medKit = medKits.find(medKitId) ?: return@run null
            val now = clock.instant()
            val pkg = Package(
                id = Uuid.random(),
                medKit = medKit.ref,
                facts = facts,
                quantity = amount,
                addedAt = now
            )
            packages.add(pkg)
            movements.record(
                StockMovement.Receipt(
                    id = Uuid.random(),
                    pkg = pkg.ref,
                    amount = amount,
                    occurredAt = now,
                    observedAt = now
                )
            )
            pkg.id
        }
}
