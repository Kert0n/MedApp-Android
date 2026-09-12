package com.kert0n.medapp.queue

import com.kert0n.medapp.queue.intake.IntakeAccounting
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Что исход доставки значит для базы — переход строки операции и список эффектов. Решает
 * очередь, там, где живёт доставка; хранение применяет список одной транзакцией и ничего не
 * толкует (PLAN E3, F5). Эффекты применяются только если [transition] изменил строку: закрытие
 * одно, и у второго закрытия следствий нет.
 */
class Settlement(val transition: Transition, effects: List<Effect> = emptyList()) {

    /** Своя копия: список, оставшийся у вызывающего, менял бы уже решённое. */
    val effects: List<Effect> = effects.toList()

    /** Что становится со строкой операции. */
    sealed interface Transition {

        /** Операция закрыта — применена, отказана или потеряла доступ; [lastError] — причина отказа. */
        data class Close(val status: SyncOperationStatus, val lastError: String? = null) : Transition {
            init {
                require(status.isClosed) { "закрытие ведёт в закрытое состояние, а не в $status" }
            }
        }

        /** Запрос сброшен, операция снова ждёт под тем же номером; факт о запросе умирает с ним. */
        data class Reprepare(val lastError: String, val notBefore: Instant? = null) : Transition

        /** Операция снова ждёт тем же запросом; попытка и неизвестный исход — как сказал [Delivery.Retry]. */
        data class Retry(
            val lastError: String,
            val attempted: Boolean,
            val outcomeUnknown: Boolean,
            val notBefore: Instant? = null
        ) : Transition
    }

    /** Что ещё меняется в базе вместе с переходом. */
    sealed interface Effect {

        /** Разрешённый снимок ложится поверх подтверждённого остатка и броней; старее нынешнего — нет. */
        data class LayDown(val snapshot: PackageSnapshot) : Effect

        /** Пачки на сервере больше нет: истина — ноль, локально она архивируется, брони сняты. */
        data class PackageGone(val packageId: Uuid) : Effect

        /** Доступа к пачке больше нет: она помечена, брони сняты. */
        data class PackageLost(val packageId: Uuid) : Effect

        /** Учёт расхода у приёма, который поставил эту операцию. */
        data class Account(val accounting: IntakeAccounting) : Effect

        /** Незакрытые зависимые закрываются [status], их приёмы получают [accounting]; и так до конца цепочки. */
        data class Cascade(val status: SyncOperationStatus, val accounting: IntakeAccounting) : Effect
    }
}

/**
 * Исход доставки команды [command] — в переход и эффекты. Чистая функция: проверяется без базы,
 * а в хранении не остаётся ветвления по видам доставки (PLAN E3).
 */
fun Delivery.settlement(command: SyncCommand): Settlement = when (this) {
    is Delivery.Applied -> Settlement(
        Settlement.Transition.Close(SyncOperationStatus.APPLIED),
        listOf(Settlement.Effect.Account(IntakeAccounting.REMOTE_APPLIED)) + state.effects(command)
    )
    is Delivery.Stale -> Settlement(
        Settlement.Transition.Reprepare("устарело: ${snapshot.sync.version}", notBefore),
        listOf(Settlement.Effect.LayDown(snapshot))
    )
    is Delivery.Refused -> Settlement(
        Settlement.Transition.Close(SyncOperationStatus.REFUSED, reason.name),
        listOf(Settlement.Effect.Account(IntakeAccounting.REMOTE_REFUSED)) + state.effects(command) +
            Settlement.Effect.Cascade(SyncOperationStatus.REFUSED, IntakeAccounting.REMOTE_REFUSED)
    )
    is Delivery.Retry -> Settlement(
        Settlement.Transition.Retry(error, attempted, outcomeUnknown, notBefore)
    )
    Delivery.AccessLost -> Settlement(
        Settlement.Transition.Close(SyncOperationStatus.ACCESS_LOST),
        listOf(Settlement.Effect.Account(IntakeAccounting.REMOTE_REFUSED)) +
            listOfNotNull((command as? PackageSyncCommand)?.let { Settlement.Effect.PackageLost(it.packageId) }) +
            Settlement.Effect.Cascade(SyncOperationStatus.ACCESS_LOST, IntakeAccounting.REMOTE_REFUSED)
    )
}

/** Истина по пачке после закрытия — что положить: снимок, «пачки нет» либо ничего. */
private fun PackageState.effects(command: SyncCommand): List<Settlement.Effect> = when (this) {
    is PackageState.Present -> listOf(Settlement.Effect.LayDown(snapshot))
    PackageState.Gone -> listOfNotNull((command as? PackageSyncCommand)?.let { Settlement.Effect.PackageGone(it.packageId) })
    PackageState.None -> emptyList()
}
