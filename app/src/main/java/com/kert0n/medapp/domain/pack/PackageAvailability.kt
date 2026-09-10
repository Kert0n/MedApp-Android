package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Три величины «сколько доступно» по одной пачке (PLAN D4) — посчитанные, а не сохранённые:
 * хранимое поле разъехалось бы с очередью и с курсами при первом чужом изменении.
 *
 * **Значение, и сущности внутри нет.** Пока проекция держала внутри `Package`, её равенство
 * сравнивало пачку по `id`: в локальной аптечке расход 20 → 19 не менял ничего другого, и две
 * проекции с разными количествами оказывались равными — ровно та ошибка, против которой написано
 * решение C1 «Состояние экрана». Здесь все поля — величины, поэтому равенство содержательное.
 *
 * Производные числа — геттеры, а не поля: все входы их арифметики лежат рядом, и рукописный
 * конструктор не сможет записать «свободно 5» там, где остаток ноль.
 *
 * Сегодняшний день не хранится: годность спрашивают датой ([isExpiredOn]), потому что прогнозу
 * нужна дата отчёта, а списку — сегодняшняя. Хранимое `today` устаревало бы молча. Сам срок
 * лежит здесь величиной [ExpiryDate] — пачки у проекции нет, а правило годности одно на обоих.
 *
 * Не путать с `PackageAccess.AVAILABLE`: тот отвечает, видим ли мы пачку вообще, а это — сколько
 * из неё доступно.
 */
data class PackageAvailability(
    val packageId: Uuid,
    val expiresOn: ExpiryDate?,
    val amount: EffectiveAmount,
    val reservedByOthers: Quantity,
    val myAllocation: Quantity
) {

    /** `null` при требуемой сверке: точного «сколько есть» до неё не обещается. */
    val effective: Quantity? get() = amount.quantityOrNull

    /**
     * Сколько я могу забрать под свой курс. Локальная оценка по последним сведениям, а не
     * гарантия серверной блокировки запаса.
     *
     * Своя бронь отсюда **не** вычитается: заявил её я сам, и вычитать её из своего же доступного
     * значило бы отнять у себя собственные таблетки. Вычитается только чужое.
     */
    val availableToMe: Quantity? get() = effective?.minusOrZero(reservedByOthers)

    /**
     * `null` = точное свободное неизвестно.
     *
     * Считается от [availableToMe], а не как `effective − claims.total`: сумма броней включает
     * мою часть, а та отстаёт от локального выделения ровно на то, что ещё не уехало. Смешав
     * свежий остаток со старой суммой, мы занизили бы свободное на собственные таблетки (D4).
     */
    val freeForAnyone: Quantity? get() = availableToMe?.minusOrZero(myAllocation)

    val requiresRecount: Boolean get() = amount is EffectiveAmount.NeedsRecount

    /** Просрочка ничего не делает сама: количество не списывается, пачка остаётся источником. */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isExpiredOn(date) == true

    fun expiresSoonOn(date: LocalDate): Boolean =
        expiresOn?.expiresWithin(date, ExpiryDate.SOON_DAYS) == true
}
