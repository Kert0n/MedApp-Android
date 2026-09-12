package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAfter
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
     * Применяет переход к нынешнему состоянию пачки: что с ней стало и чем это объясняется,
     * отвечает сама пачка — «было» она знает, а «сколько ушло на самом деле» её правило (PLAN D7).
     * Хранение называет действие и записывает ответ, но не решает, что действие значит.
     *
     * У переноса следа нет: остаток он не меняет, а где коробка лежит, знает сама пачка.
     */
    fun applyTo(pack: Package, at: Instant): PackageAfter {
        require(pack.id == packageId) { "переход применяется к своей пачке" }
        return when (this) {
            is Recount -> pack.correctTo(actual, movementId, at, note)
            is Disposal -> pack.dispose(amount, movementId, at, reason, note)
            is Transfer -> PackageAfter.Left(pack.moveTo(target))
        }
    }
}
