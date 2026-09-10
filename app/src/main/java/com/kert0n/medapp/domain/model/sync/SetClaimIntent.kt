package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Заявить бронь на упаковку — серверное представление невыбранного выделения источника курса
 * (PLAN D5).
 *
 * Величина **абсолютная**: сервер хранит одну бронь на пару «человек и упаковка», и целевой
 * объём равен `allocatedDoses × dose`. Ноль здесь не пишут: снятие — это `ReleaseClaimIntent`,
 * и на проводе у него другая операция.
 */
data class SetClaimIntent(val packageId: Uuid, val amount: Quantity) : SyncIntent {
    init {
        require(!amount.isZero) { "нулевая бронь — это снятие брони, у него свой вид" }
    }
}
