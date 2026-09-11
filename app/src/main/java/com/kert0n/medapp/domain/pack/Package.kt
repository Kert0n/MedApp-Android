package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.intake.IntakeRejected
import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Упаковка — конкретная пачка или флакон; одинаковые названия пачки не объединяют (PLAN C0).
 * Сущность: пачка, из которой приняли таблетку, — та же пачка, равенство по [id], состояние
 * меняют переходы. [quantity] — подтверждённый остаток (E1); обвязка синхронизации живёт в
 * `PackageSyncState` слоя данных. Аптечку пачка держит объектом: где она лежит и опубликована
 * ли, спрашивают у неё, а не ищут по номеру.
 *
 * Объект действителен в пределах транзакции, которая его прочитала: пачка на руках после
 * первого же приёма — пачка с прежним остатком, если её не перечитать.
 */
class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKit: MedKit,
    val facts: PackageFacts,
    val quantity: Quantity,
    val addedAt: Instant,         // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid? = null, // из какой карточки справочника заполнено
    val claims: Claims? = null,   // null у неопубликованной аптечки
    val lifecycle: Lifecycle = Lifecycle.ACTIVE,
    val access: Access = Access.AVAILABLE
) {

    init {
        require(lifecycle != Lifecycle.ACTIVE || !quantity.isZero) {
            "активная пачка не бывает пустой"
        }
        // Подсказка — это «сколько я обычно принимаю из ЭТОЙ пачки»: величина в чужой единице
        // не подставится в форму приёма и молча притворилась бы подходящей.
        val hint = facts.defaultIntakeAmount
        require(hint == null || hint.unit == quantity.unit) {
            "доза-подсказка измеряется той же единицей, что остаток пачки"
        }
    }

    val name: String get() = facts.name

    /**
     * Берут ли из этой пачки: она цела и мы её видим. Одно правило на всех, кто спрашивает
     * «можно ли отсюда взять» — переходы пачки, расчёт свободного, фильтр списка (PLAN D4, D5).
     */
    val suppliesStock: Boolean
        get() = lifecycle == Lifecycle.ACTIVE && access == Access.AVAILABLE

    fun isExpiredOn(date: LocalDate): Boolean = facts.isExpiredOn(date)

    fun expiresWithin(date: LocalDate, days: Long): Boolean = facts.expiresWithin(date, days)

    /**
     * Акт «беру из этой пачки»: она цела, мы её видим, и дозу считают в её единице. Правило стоит
     * в момент записи и проверяется по пачке, какой её знает устройство сейчас, — другой у него
     * нет; когда приём случился, называет человек через [at]. Записанный факт этой проверке больше
     * не подлежит (PLAN D6). Остаток акт не меняет: списывает [consume] по состоянию в базе.
     */
    fun take(amount: Dose, at: Instant): Result<TakenDose> {
        val rejection = when {
            !suppliesStock -> IntakeRejected.Reason.PACKAGE_UNUSABLE
            amount.unit != quantity.unit -> IntakeRejected.Reason.UNIT_MISMATCH
            else -> null
        }
        return if (rejection == null) Result.success(TakenDose(this, amount, at))
        else Result.failure(IntakeRejected(rejection))
    }

    /**
     * Расход — приём, плановый или разовый. В минус не списывает (PLAN D5); пачка,
     * израсходованная до нуля, архивируется.
     */
    fun consume(amount: Dose): Package {
        requireUsable("расход")
        return withQuantity(quantity - amount.quantity)
    }

    /**
     * Утилизация: выбросили [amount] — просроченное, испорченное. В минус пачка не уходит, поэтому
     * «выбросил больше, чем было» списывает остаток целиком, и ушедшее в ноль архивируется.
     * Сколько ушло на самом деле, видно по разнице остатков — это и записывает история (PLAN D7).
     */
    fun dispose(amount: Quantity): Package {
        requireUsable("утилизация")
        return withQuantity(quantity.minusOrZero(amount))
    }

    /**
     * Пересчёт: «пересчитал и увидел столько» — замена значения, а не дельта (E1). Единица та же:
     * смена единицы — отдельный сценарий (D3).
     */
    fun correctTo(actual: Quantity): Package {
        requireUsable("пересчёт")
        require(actual.unit == quantity.unit) {
            "пересчёт не меняет единицу: это отдельный сценарий"
        }
        return withQuantity(actual)
    }

    /** Заменяет описательные сведения целиком — и серверные поля, и локальные (PLAN D3). */
    fun describe(facts: PackageFacts): Package {
        requireUsable("правка описания")
        return changed(facts = facts)
    }

    /**
     * Перенос меняет только принадлежность; что делать с бронями на границе публикации, решает
     * сценарий переноса (PLAN E6).
     *
     * Принимает саму аптечку, а не её идентификатор: у вызывающего она на руках, а подставить
     * вместо неё чужой `Uuid` — пачки, формы, единицы — тогда становится нечем.
     */
    fun moveTo(target: MedKit): Package {
        requireUsable("перенос")
        require(target != medKit) { "пачка уже лежит в этой аптечке" }
        return changed(medKit = target)
    }

    /**
     * Утилизация или удаление человеком; повтор ничего не меняет. Доступ не трогается: выбросить
     * пачку и потерять к ней доступ можно в любом порядке.
     */
    fun archive(): Package =
        if (lifecycle == Lifecycle.ARCHIVED) this
        else changed(lifecycle = Lifecycle.ARCHIVED)

    /**
     * Доступ утрачен: вышли из аптечки, её унесли или удалили; повтор ничего не меняет. Брони
     * снимаются вместе с доступом (PLAN D5).
     */
    fun loseAccess(): Package =
        if (access == Access.LOST) this
        else changed(access = Access.LOST, claims = null)

    private fun withQuantity(left: Quantity): Package = changed(
        quantity = left,
        lifecycle = if (left.isZero) Lifecycle.ARCHIVED else lifecycle
    )

    /**
     * Архивную и недоступную пачку не правят; возврат из архива, если понадобится, будет
     * отдельным явным действием.
     */
    private fun requireUsable(action: String) {
        check(lifecycle == Lifecycle.ACTIVE) {
            "$action недоступен для пачки в состоянии $lifecycle"
        }
        check(access == Access.AVAILABLE) {
            "$action недоступен: доступ к пачке утрачен"
        }
    }

    /**
     * Изменённый экземпляр; [id] и [addedAt] не меняются. Явный `claims = null` очищает брони,
     * непереданный аргумент их сохраняет.
     */
    private fun changed(
        medKit: MedKit = this.medKit,
        facts: PackageFacts = this.facts,
        quantity: Quantity = this.quantity,
        templateId: Uuid? = this.templateId,
        claims: Claims? = this.claims,
        lifecycle: Lifecycle = this.lifecycle,
        access: Access = this.access
    ): Package = Package(
        id = id,
        medKit = medKit,
        facts = facts,
        quantity = quantity,
        addedAt = addedAt,
        templateId = templateId,
        claims = claims,
        lifecycle = lifecycle,
        access = access
    )

    /** Тождество — [id]. Пачка, из которой приняли таблетку, та же самая пачка. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Package && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Package(id=$id, name=${facts.name}, lifecycle=$lifecycle, access=$access)"

    /**
     * Жива ли пачка как вещь. Отдельная ось от [Access]: выбросить пачку и потерять к ней доступ
     * можно в любом порядке, и оба факта нужны истории.
     */
    enum class Lifecycle {
        ACTIVE,
        ARCHIVED    // израсходована, утилизирована или удалена человеком
    }

    /**
     * Видим ли мы пачку на сервере — состояние нашего доступа, а не самой пачки: она цела и лежит
     * в аптечке, из которой мы вышли.
     */
    enum class Access { AVAILABLE, LOST }
}
