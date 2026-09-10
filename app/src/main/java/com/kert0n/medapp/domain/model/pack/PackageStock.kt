package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.course.CourseBrief
import com.kert0n.medapp.domain.model.sync.ConsumeIntent
import com.kert0n.medapp.domain.model.sync.CorrectStockIntent
import com.kert0n.medapp.domain.model.sync.CreateMedKitIntent
import com.kert0n.medapp.domain.model.sync.CreatePackageIntent
import com.kert0n.medapp.domain.model.sync.DeleteMedKitIntent
import com.kert0n.medapp.domain.model.sync.DeletePackageIntent
import com.kert0n.medapp.domain.model.sync.DescribePackageIntent
import com.kert0n.medapp.domain.model.sync.LeaveMedKitIntent
import com.kert0n.medapp.domain.model.sync.MovePackageIntent
import com.kert0n.medapp.domain.model.sync.ReconcileStockIntent
import com.kert0n.medapp.domain.model.sync.ReleaseClaimIntent
import com.kert0n.medapp.domain.model.sync.SetClaimIntent
import com.kert0n.medapp.domain.model.sync.SyncIntent
import com.kert0n.medapp.domain.model.value.Quantity
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Три с половиной величины «сколько доступно» по одной пачке, посчитанные вместе (PLAN D4).
 *
 * Это **доменная проекция для расчётов**, а не состояние экрана: названия единицы и формы,
 * строки и порядок добавляет адаптер представления. Ничего не хранится — иначе разъедется с
 * очередью и с курсами.
 *
 * [pendingIntents] — незакрытые намерения по **этой** пачке в порядке `sequence`. Порядок и
 * отбор — работа слоя данных: номер очереди принадлежит базе, которая его выдаёт, а домен
 * получает намерения уже готовым списком (PLAN E1, E2).
 *
 * [unresolvedOperationIds] — операции с неустановленным исходом. Домен эти номера не толкует, а
 * переносит в [StockViewState.NeedsRecount]: вопрос «включён ли наш расход в серверный остаток»
 * решается очередью, но ответить «остаток такой-то» до его решения нельзя, и знать об этом
 * обязаны все расчёты, а не только экран.
 *
 * [myAllocation] считается по активным источникам курсов и приходит аргументом: пачка не знает,
 * кому она выделена, и спрашивать курсы у неё было бы обратной зависимостью.
 */
data class PackageStock(
    val pkg: Package,
    val today: LocalDate,
    val pendingIntents: List<SyncIntent> = emptyList(),
    val unresolvedOperationIds: List<Uuid> = emptyList(),
    val myAllocation: Quantity = Quantity.zero(pkg.quantity.unitId),
    val course: CourseBrief? = null
) {

    init {
        require(myAllocation.unitId == pkg.quantity.unitId) {
            "выделение измеряется единицей пачки"
        }
        pendingIntents.forEach { intent ->
            require(intent.packageIdOrNull() == pkg.id) {
                "намерение $intent относится не к этой пачке"
            }
        }
    }

    /**
     * Остаток: число или требование сверки.
     *
     * Неустановленный исход перебивает проекцию целиком, а не по частям: пока неизвестно,
     * включён ли расход в серверный остаток, любое число было бы догадкой (PLAN E1, E3).
     */
    val stock: StockViewState
        get() = if (unresolvedOperationIds.isNotEmpty()) {
            StockViewState.NeedsRecount(pkg.quantity, unresolvedOperationIds)
        } else {
            StockViewState.Known(projected(), pendingIntents.isNotEmpty())
        }

    /** `null` при требуемой сверке: точного «сколько есть» до неё не обещается. */
    val effective: Quantity? get() = (stock as? StockViewState.Known)?.effective

    /**
     * Чужие брони: сумма всех минус моя часть.
     *
     * Считается **не** как `effective − claims.total`: сумма включает мою часть, а та отстаёт от
     * локального выделения ровно на то, что ещё не уехало. Смешивать свежий локальный остаток со
     * старой суммой броней нельзя — занизили бы свободное на собственные таблетки (PLAN D4).
     */
    val reservedByOthers: Quantity
        get() {
            val claims = pkg.claims ?: return zero
            val mine = claims.mine ?: BigDecimal.ZERO
            return Quantity(maxOf(claims.total - mine, BigDecimal.ZERO), zero.unitId)
        }

    /**
     * Своя бронь без локального владельца: курса, который бы её объяснял, нет, и снятия мы не
     * ждём.
     *
     * Её **не снимают и не переназначают молча** (PLAN D4): человек сначала разбирает
     * неизвестное назначение. Поэтому в свободном она вычитается наравне с чужой.
     */
    val orphanClaim: Quantity
        get() {
            val mine = pkg.claims?.mine ?: return zero
            if (course != null || releaseExpected) return zero
            return Quantity(mine, zero.unitId)
        }

    /** `null` = точное свободное неизвестно. */
    val freeForAnyone: Quantity?
        get() = effective
            ?.minusOrZero(reservedByOthers)
            ?.minusOrZero(myAllocation)
            ?.minusOrZero(orphanClaim)

    /**
     * Сколько я могу забрать под свой курс. Локальная оценка по последним сведениям, а не
     * гарантия серверной блокировки запаса (PLAN D4).
     */
    val availableToMe: Quantity?
        get() = effective?.minusOrZero(reservedByOthers)?.minusOrZero(orphanClaim)

    /** Просрочка ничего не делает сама: количество не списывается, пачка остаётся источником. */
    val isExpired: Boolean get() = pkg.isExpiredOn(today)

    val expiresSoon: Boolean get() = pkg.expiresWithin(today, PACKAGE_EXPIRES_SOON_DAYS)

    private val zero: Quantity get() = Quantity.zero(pkg.quantity.unitId)

    private val releaseExpected: Boolean
        get() = pendingIntents.any { it is ReleaseClaimIntent || it is SetClaimIntent }

    /**
     * Последовательное применение незакрытых намерений к подтверждённому остатку (PLAN E1).
     *
     * Расход вычитается **один раз**, пересчёт и сверка **заменяют** значение — это не дельта, —
     * удаление даёт ноль, а описательная правка и брони количества не меняют. `CreatePackage`
     * ничего не прибавляет: начальная база уже зафиксирована локально, и второй приход по ней не
     * создаётся.
     *
     * Отсечение прежних намерений сверкой делает слой данных: `throughSequence` — номер очереди,
     * и владеет им база. Здесь список уже приходит в порядке и без отсечённого, поэтому сверка
     * просто называет остаток.
     *
     * Отрицательный расчёт показывается нулём: нехватка — это конфликт операции, а не успешный
     * расход, и разбирается он по состоянию очереди, а не подменой числа.
     */
    private fun projected(): Quantity {
        var base = pkg.quantity
        for (intent in pendingIntents) {
            base = when (intent) {
                is ConsumeIntent -> base.minusOrZero(intent.amount)
                is CorrectStockIntent -> intent.actual
                is ReconcileStockIntent -> intent.actual
                is DeletePackageIntent -> Quantity.zero(base.unitId)
                else -> base
            }
        }
        return base
    }
}

/**
 * Какой пачки касается намерение. Возвращает `null` у намерений об аптечке: они относятся ко
 * всей аптечке сразу, и в список одной пачки попадать не должны.
 *
 * Общего поля `packageId` у [SyncIntent] нет намеренно: вид задаёт смысл полей, и у создания
 * аптечки пачки нет вовсе. Этот `when` — единственное место, где вопрос задаётся, и он
 * исчерпывающий по видам.
 */
private fun SyncIntent.packageIdOrNull(): Uuid? = when (this) {
    is CreateMedKitIntent, is DeleteMedKitIntent, is LeaveMedKitIntent -> null
    is CreatePackageIntent -> packageId
    is DescribePackageIntent -> packageId
    is CorrectStockIntent -> packageId
    is MovePackageIntent -> packageId
    is DeletePackageIntent -> packageId
    is ConsumeIntent -> packageId
    is SetClaimIntent -> packageId
    is ReleaseClaimIntent -> packageId
    is ReconcileStockIntent -> packageId
}
