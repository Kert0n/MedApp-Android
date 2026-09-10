package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Остаток пачки глазами очереди: подтверждённое сервером число, незакрытые команды в порядке
 * `sequence` — уже без отсечённого ручной сверкой — и операции с неустановленным исходом
 * (PLAN E1, E3). Домену отдаётся [amount]; признаки очереди остаются здесь, и экран сводит их с
 * доменным результатом сам.
 */
data class PackageQueueState(
    val packageId: Uuid,
    val confirmed: Quantity,
    val unclosed: List<PackageSyncCommand> = emptyList(),
    val unresolvedOperationIds: List<Uuid> = emptyList()
) {

    init {
        // Чужая команда в этой свёртке дала бы неверный остаток молча. Раньше проверять было
        // нечем: состояние не знало, чьё оно.
        require(unclosed.all { it.packageId == packageId }) {
            "в остаток пачки $packageId сворачиваются только её команды"
        }
    }

    /**
     * Собирается из пачки: подтверждённое число и тождество берутся у неё, поэтому соединить
     * остаток одной пачки с идентификатором другой нечем.
     */
    constructor(
        pkg: Package,
        unclosed: List<PackageSyncCommand> = emptyList(),
        unresolvedOperationIds: List<Uuid> = emptyList()
    ) : this(pkg.id, pkg.quantity, unclosed, unresolvedOperationIds)

    /**
     * Количество для домена: подтверждённое число с применёнными по порядку командами. При
     * неустановленном исходе — [EffectiveAmount.Unknown]: вошёл ли наш расход в серверный
     * остаток, неизвестно, и любое число было бы догадкой.
     */
    val amount: EffectiveAmount
        get() =
            if (unresolvedOperationIds.isNotEmpty()) EffectiveAmount.Unknown
            else EffectiveAmount.Known(projected.amount)

    /** В число вложено незакрытое изменение количества; правка описания или брони не в счёт. */
    val hasUnconfirmedChanges: Boolean get() = projected.changed

    /**
     * Одна свёртка на оба вопроса. Прежде «меняет ли команда количество» спрашивалось зондом —
     * применением к произвольному числу и проверкой на `null`; теперь это то, что свёртка и так
     * узнала по дороге.
     */
    private val projected: Projected =
        unclosed.fold(Projected(confirmed, changed = false)) { acc, command ->
            val next = command.appliedTo(acc.amount) ?: return@fold acc
            Projected(next, changed = true)
        }

    private data class Projected(val amount: Quantity, val changed: Boolean)
}
