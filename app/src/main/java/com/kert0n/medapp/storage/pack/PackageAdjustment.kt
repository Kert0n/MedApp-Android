package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.storage.server.QueuedCommand

/**
 * Изменение остатка или места пачки вместе с его следом: пересчёт, утилизация, архивирование,
 * перенос между аптечками. Движение и новое состояние пачки ложатся одной транзакцией — иначе
 * отчёт разошёлся бы с остатком (PLAN F5, D7).
 *
 * Выделения курса пересчитывает домен и отдаёт сюда готовыми: при нехватке зажимается
 * обеспечение, а расписание и доза не меняются (PLAN D5).
 */
class PackageAdjustment(
    val pack: Package,
    val movement: StockMovement,
    val sync: PackageSyncState = PackageSyncState(pack.id),
    val course: Course? = null,
    val command: QueuedCommand? = null
) {
    init {
        require(movement.packageId == pack.id) { "движение записывается по своей пачке" }
        require(sync.packageId == pack.id) { "обвязка синхронизации принадлежит своей пачке" }
    }
}
