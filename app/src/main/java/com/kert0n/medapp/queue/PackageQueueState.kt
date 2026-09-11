package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.pack.PackageSyncCommand
import kotlin.uuid.Uuid

/**
 * Остаток пачки глазами очереди: подтверждённое сервером число и незакрытые команды в порядке
 * `sequence` (PLAN E1). Домену отдаётся [amount]; признаки очереди остаются здесь, и экран
 * сводит их с доменным результатом сам.
 */
class PackageQueueState(
    val packageId: Uuid,
    val confirmed: Quantity,
    unclosed: List<PackageSyncCommand> = emptyList()
) {

    /**
     * Своя копия: свёртка считается один раз при сборке, и список, оставшийся у вызывающего,
     * иначе расходился бы с уже посчитанным остатком.
     */
    val unclosed: List<PackageSyncCommand> = unclosed.toList()

    init {
        // Чужая команда в этой свёртке дала бы неверный остаток молча.
        require(unclosed.all { it.packageId == packageId }) {
            "в остаток пачки $packageId сворачиваются только её команды"
        }
    }

    /**
     * Собирается из пачки: подтверждённое число и тождество берутся у неё, поэтому соединить
     * остаток одной пачки с идентификатором другой нечем.
     */
    constructor(pkg: Package, unclosed: List<PackageSyncCommand> = emptyList()) :
        this(pkg.id, pkg.quantity, unclosed)

    /**
     * Количество для домена: подтверждённое число с применёнными по порядку командами. Число есть
     * всегда: команда, которая ещё не доехала, уже отправлена или ждёт повтора, и устройство
     * знает, что именно оно отправило; истину потом читает снимок.
     */
    val amount: Quantity get() = projected.amount

    /** В число вложено незакрытое изменение количества; правка описания или брони не в счёт. */
    val hasUnconfirmedChanges: Boolean get() = projected.changed

    /** Одна свёртка на оба вопроса: меняла ли команда количество, она узнаёт по дороге. */
    private val projected: Projected =
        unclosed.fold(Projected(confirmed, changed = false)) { acc, command ->
            val next = command.appliedTo(acc.amount) ?: return@fold acc
            // Пересчёт заменяет число целиком, поэтому чужая единица сменила бы её молча.
            require(next.unit == confirmed.unit) { "команда пачки измеряется её единицей" }
            Projected(next, changed = true)
        }

    private data class Projected(val amount: Quantity, val changed: Boolean)
}
