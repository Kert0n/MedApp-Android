package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Остаток пачки глазами очереди: подтверждённое сервером число, незакрытые команды в порядке
 * `sequence` — уже без отсечённого ручной сверкой — и операции с неустановленным исходом
 * (PLAN E1, E3). Домену отдаётся [amount]; признаки очереди остаются здесь, и экран сводит их с
 * доменным результатом сам.
 */
data class PackageQueueState(
    val confirmed: Quantity,
    val unclosed: List<PackageSyncCommand> = emptyList(),
    val unresolvedOperationIds: List<Uuid> = emptyList()
) {

    /**
     * Количество для домена: подтверждённое число с применёнными по порядку командами. При
     * неустановленном исходе — [EffectiveAmount.Unknown]: вошёл ли наш расход в серверный
     * остаток, неизвестно, и любое число было бы догадкой.
     */
    val amount: EffectiveAmount
        get() {
            if (unresolvedOperationIds.isNotEmpty()) return EffectiveAmount.Unknown
            val projected = unclosed.fold(confirmed) { amount, command ->
                command.appliedTo(amount) ?: amount
            }
            return EffectiveAmount.Known(projected)
        }

    /** В число вложено незакрытое изменение количества; правка описания или брони не в счёт. */
    val hasUnconfirmedChanges: Boolean get() = unclosed.any { it.appliedTo(confirmed) != null }
}
