package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.pack.PackageSharedFacts
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Завести упаковку на сервере.
 *
 * Начальный остаток строго положителен — и в доменном сценарии, и в POST-DTO: пачка, которой нет,
 * не заводится (PLAN E2). Личные сведения в намерении отсутствуют по типу: уезжает
 * [PackageSharedFacts], а срок годности, заметка и цена остаются на устройстве (PLAN C0).
 */
data class CreatePackageIntent(
    val packageId: Uuid,
    val medKitId: Uuid,
    val quantity: Quantity,
    val facts: PackageSharedFacts
) : SyncIntent {
    init {
        require(!quantity.isZero) { "пачка заводится с положительным остатком" }
    }
}
