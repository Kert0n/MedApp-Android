package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackagePending
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import kotlin.uuid.Uuid

/**
 * Остаток пачки глазами очереди: подтверждённое сервером число и незакрытые команды в порядке
 * `sequence` (PLAN E1). Закрытые — применённые, отказанные, потерявшие доступ — не считаются:
 * истина по ним уже прочитана снимком и лежит в подтверждённом числе. Домену отдаётся [amount];
 * признаки очереди остаются здесь, и экран сводит их с доменным результатом сам.
 *
 * Свёртка тотальна: она стоит на пути чтения, и данные, которые она видит, — законное состояние
 * базы. Команда в единице, которой пачку больше не считают (сосед сменил её на сервере, а
 * подготовка ещё не отвергла команду при взятии), в число не входит и названа в [incompatible].
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

    /**
     * Что доставка делает с коробкой — вопрос о ней самой, а не о числе (PLAN E1). Удаление
     * выделено из прочих изменений: помеченную коробку человек видит, но не пользуется ею, а
     * остальное ей не мешает. Пересчёт в ноль — то же удаление: на проводе он им и становится (B6).
     */
    val pending: PackagePending
        get() = when {
            unclosed.any { it is PackageSyncCommand.Delete || (it is PackageSyncCommand.CorrectStock && it.actual.isZero) } ->
                PackagePending.REMOVAL
            unclosed.isNotEmpty() -> PackagePending.CHANGES
            else -> PackagePending.NOTHING
        }

    /**
     * Незакрытые команды в единице, которой пачку больше не считают: ждут отказа при взятии
     * (`UNIT_CHANGED`), в число не входят. Экран называет их рядом с нечитаемыми.
     */
    val incompatible: List<PackageSyncCommand> get() = projected.incompatible

    /** Одна свёртка на все вопросы: меняла ли команда количество и применима ли она, узнаётся по дороге. */
    private val projected: Projected =
        unclosed.fold(Projected(confirmed, changed = false, incompatible = emptyList())) { acc, command ->
            val unit = command.measuredIn
            // Число без единицы сервер прочёл бы в своей: такая команда на провод не пойдёт.
            if (unit != null && unit != confirmed.unit) return@fold acc.copy(incompatible = acc.incompatible + command)
            val next = command.appliedTo(acc.amount) ?: return@fold acc
            Projected(next, changed = true, incompatible = acc.incompatible)
        }

    private data class Projected(
        val amount: Quantity,
        val changed: Boolean,
        val incompatible: List<PackageSyncCommand>
    )
}
