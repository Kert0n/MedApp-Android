package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Изменение остатка или места пачки помимо приёма: пересчёт, утилизация, перенос между аптечками
 * (PLAN D7, F5). Названо действием человека, а не его результатом: «пересчитал и увидел 17», а не
 * «пачка теперь 17».
 *
 * Разница существенна. Результат считается по тому состоянию, которое вызывающий прочитал когда-то
 * раньше, и записывается поверх нынешнего — вместе со следом, который называет «было 20», хотя в
 * базе давно 18. Переход же применяется к тому состоянию, которое лежит в базе, и «было» с «стало»
 * в его следе — настоящие. Что означает каждый переход, по-прежнему решает домен: хранение только
 * называет действие и записывает результат.
 *
 * Тождество записи движения приходит от вызывающего: повтор с тем же номером не заводит в истории
 * вторую запись. Номер спрашивается только у тех переходов, которые эту запись пишут: у переноса
 * её нет вовсе, и обещать её типом было бы неправдой.
 */
sealed interface PackageAdjustment {

    val packageId: Uuid

    val note: String?

    /** «Пересчитал и увидел столько» — замена значения, а не дельта (PLAN E1). */
    data class Recount(
        override val packageId: Uuid,
        val actual: Quantity,
        val movementId: Uuid,
        override val note: String? = null
    ) : PackageAdjustment

    /**
     * Выбросили названное количество по названной причине; ушедшая в ноль коробка кончается.
     * В историю попадает не запрошенное, а ушедшее: в минус пачка не списывается.
     */
    data class Disposal(
        override val packageId: Uuid,
        val amount: Quantity,
        val reason: StockMovement.Disposal.Reason,
        val movementId: Uuid,
        override val note: String? = null
    ) : PackageAdjustment

    /**
     * Перенос в другую аптечку: меняется место, а не остаток, и следа в истории он не оставляет.
     * Принимает саму аптечку, а не её идентификатор, — как и переход пачки, который этим
     * переносом и вызывается.
     */
    data class Transfer(
        override val packageId: Uuid,
        val target: MedKitRef,
        override val note: String? = null
    ) : PackageAdjustment

    /**
     * Применяет переход к нынешнему состоянию пачки и записывает его след. Оба конца следа —
     * «было» и «стало» — известны только здесь, потому что «было» прочитано в той же транзакции.
     *
     * У переноса следа нет: остаток он не меняет, а где коробка лежит, знает сама пачка (PLAN D7).
     */
    fun applyTo(pack: Package, at: Instant): Applied {
        require(pack.id == packageId) { "переход применяется к своей пачке" }
        return when (this) {
            is Recount -> Applied(
                pack.correctTo(actual),
                StockMovement.Recount(movementId, pack.ref, pack.quantity, actual, at, at, note)
            )
            is Disposal -> {
                // В историю идёт то, что действительно ушло, — разница остатков до и после
                // перехода: сколько уходит, когда выбросили больше, чем было, решает пачка.
                val disposed = pack.dispose(amount)
                val left = disposed?.quantity ?: Quantity.zero(pack.quantity.unit)
                Applied(
                    disposed,
                    StockMovement.Disposal(movementId, pack.ref, pack.quantity - left, reason, at, at, note)
                )
            }
            is Transfer -> Applied(pack.moveTo(target))
        }
    }

    /**
     * Новое состояние пачки и запись о том, как оно получилось. `null` вместо пачки — коробка
     * кончилась, и строки после перехода не остаётся (PLAN D3); запись бывает не у всякого
     * перехода: перенос остаток не трогает, и в истории расхода ему места нет.
     */
    data class Applied(val pack: Package?, val movement: StockMovement? = null)
}
