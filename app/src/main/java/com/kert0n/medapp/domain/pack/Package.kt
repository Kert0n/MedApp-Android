package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.intake.IntakeRejected
import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Упаковка — конкретная коробка или флакон, который у нас есть; одинаковые названия пачки не
 * объединяют (PLAN C0). Сущность: пачка, из которой приняли таблетку, — та же пачка, равенство по
 * [id], состояние меняют переходы. [quantity] — подтверждённый остаток (E1); обвязка
 * синхронизации живёт в `PackageSyncState` слоя данных. Аптечку пачка держит ссылкой [MedKitRef].
 *
 * Состояний у коробки нет: она либо есть, либо её нет. Пустой коробки не бывает — кончившаяся
 * (расход, утилизация, пересчёт в ноль) перестаёт существовать так же, как выброшенная, и
 * переходы отвечают на это `null` (PLAN D3). Что от неё остаётся навсегда — [record]: за неё
 * держатся приёмы и движения (D6, D7).
 *
 * Объект действителен в пределах транзакции, которая его прочитала: пачка на руках после
 * первого же приёма — пачка с прежним остатком, если её не перечитать.
 */
class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKit: MedKitRef,
    val facts: PackageFacts,
    val quantity: Quantity,
    val addedAt: Instant,         // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid? = null, // из какой карточки справочника заполнено
    val claims: Claims? = null    // null у неопубликованной аптечки
) {

    init {
        require(!quantity.isZero) { "пустой коробки не бывает: кончившаяся удаляется" }
        // Подсказка — это «сколько я обычно принимаю из ЭТОЙ пачки»: величина в чужой единице
        // не подставится в форму приёма и молча притворилась бы подходящей.
        val hint = facts.defaultIntakeAmount
        require(hint == null || hint.unit == quantity.unit) {
            "доза-подсказка измеряется той же единицей, что остаток пачки"
        }
    }

    val name: String get() = facts.name

    /**
     * Как пачку видит экран: состояние вместе с доступностью, посчитанной тем, кто читал очередь и
     * выделения (PLAN D4, E1). Величина — наружу уходит она, а не сущность.
     */
    fun projection(availability: PackageAvailability, hasUnconfirmedChanges: Boolean): PackageProjection =
        PackageProjection(
            id = id,
            medKit = medKit,
            facts = facts,
            quantity = quantity,
            addedAt = addedAt,
            templateId = templateId,
            claims = claims,
            availability = availability,
            hasUnconfirmedChanges = hasUnconfirmedChanges
        )

    /**
     * Вечная запись о коробке: снимок имени, единицы и формы идёт за живой пачкой, момент
     * появления — её собственный (PLAN D3). Хранение пишет запись вместе с пачкой.
     */
    val record: PackageRecord
        get() = PackageRecord(id = id, name = facts.name, unit = quantity.unit, form = facts.form, addedAt = addedAt)

    /** Как пачку видит чужой агрегат — курс, приём, движение: ссылка на запись, без переходов. */
    val ref: PackageRef get() = record.ref

    fun isExpiredOn(date: LocalDate): Boolean = facts.isExpiredOn(date)

    fun expiresWithin(date: LocalDate, days: Long): Boolean = facts.expiresWithin(date, days)

    /**
     * Акт «беру из этой пачки»: дозу считают в её единице. Правило стоит в момент записи и
     * проверяется по пачке, какой её знает устройство сейчас; когда приём случился, называет
     * человек через [at]. Записанный факт этой проверке больше не подлежит (PLAN D6). Остаток
     * акт не меняет: списывает [consume] по состоянию в базе.
     */
    fun take(amount: Dose, at: Instant): Result<TakenDose> =
        if (amount.unit != quantity.unit) Result.failure(IntakeRejected(IntakeRejected.Reason.UNIT_MISMATCH))
        else Result.success(TakenDose(ref, amount, at))

    /**
     * Расход — приём, плановый или разовый. В минус не списывает (PLAN D5); `null` — коробка
     * кончилась, и её больше нет.
     */
    fun consume(amount: Dose): Package? = withQuantity(quantity - amount.quantity)

    /**
     * Утилизация: выбросили [amount] — просроченное, испорченное. В минус пачка не уходит, поэтому
     * «выбросил больше, чем было» списывает остаток целиком. Сколько ушло на самом деле, видно по
     * разнице остатков — это и записывает история (PLAN D7). `null` — коробка кончилась.
     */
    fun dispose(amount: Quantity): Package? = withQuantity(quantity.minusOrZero(amount))

    /**
     * Пересчёт: «пересчитал и увидел столько» — замена значения, а не дельта (E1). Единица та же:
     * смена единицы — отдельный сценарий (D3). Ноль — коробки больше нет: `null`.
     */
    fun correctTo(actual: Quantity): Package? {
        require(actual.unit == quantity.unit) {
            "пересчёт не меняет единицу: это отдельный сценарий"
        }
        return withQuantity(actual)
    }

    /** Заменяет описательные сведения целиком — и серверные поля, и локальные (PLAN D3). */
    fun describe(facts: PackageFacts): Package = changed(facts = facts)

    /**
     * Перенос меняет только принадлежность; что делать с бронями на границе публикации, решает
     * сценарий переноса (PLAN E6).
     *
     * Принимает ссылку на аптечку, а не её идентификатор: у вызывающего она на руках, а
     * подставить вместо неё чужой `Uuid` — пачки, формы, единицы — тогда становится нечем.
     */
    fun moveTo(target: MedKitRef): Package {
        require(target != medKit) { "пачка уже лежит в этой аптечке" }
        return changed(medKit = target)
    }

    /**
     * Доступ утрачен: вышли из аптечки, её унесли или удалили. Коробка цела, но не у нас, и
     * последний виденный остаток уходит из учёта записью в историю (PLAN D7); самой пачки после
     * этого не остаётся. Тождество записи называет вызывающий: повтор не заводит вторую.
     */
    fun lost(movementId: Uuid, at: Instant): StockMovement.AccessLoss =
        StockMovement.AccessLoss(movementId, ref, quantity, observedAt = at)

    private fun withQuantity(left: Quantity): Package? =
        if (left.isZero) null else changed(quantity = left)

    /**
     * Изменённый экземпляр; [id] и [addedAt] не меняются. Явный `claims = null` очищает брони,
     * непереданный аргумент их сохраняет.
     */
    private fun changed(
        medKit: MedKitRef = this.medKit,
        facts: PackageFacts = this.facts,
        quantity: Quantity = this.quantity,
        templateId: Uuid? = this.templateId,
        claims: Claims? = this.claims
    ): Package = Package(
        id = id,
        medKit = medKit,
        facts = facts,
        quantity = quantity,
        addedAt = addedAt,
        templateId = templateId,
        claims = claims
    )

    /** Тождество — [id]. Пачка, из которой приняли таблетку, та же самая пачка. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Package && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Package(id=$id, name=${facts.name}, quantity=$quantity)"
}
