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
 * (расход, утилизация, пересчёт в ноль) перестаёт существовать так же, как выброшенная, и переходы
 * отвечают на это [PackageAfter.Ended] с [PackageEnding] внутри: у конца есть след, объясняющий,
 * куда делся остаток, и выбирает его переход, а не тот, кто записывает (PLAN D3, H6). Что от
 * коробки остаётся навсегда — [record]: за неё держатся приёмы и движения (D6, D7).
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
    fun projection(
        availability: PackageAvailability,
        hasUnconfirmedChanges: Boolean,
        pending: PackagePending = PackagePending.NOTHING
    ): PackageProjection =
        PackageProjection(
            id = id,
            medKit = medKit,
            facts = facts,
            quantity = quantity,
            addedAt = addedAt,
            templateId = templateId,
            claims = claims,
            availability = availability,
            hasUnconfirmedChanges = hasUnconfirmedChanges,
            pending = pending
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
     * Расход — приём, плановый или разовый. В минус не списывает (PLAN D5). Следа в истории
     * расход не оставляет: приём и есть учётная запись о нём (PLAN D7, H6).
     */
    fun consume(amount: Dose): PackageAfter = after(quantity - amount.quantity, trace = null)

    /**
     * Утилизация: выбросили [amount] — просроченное, испорченное. В минус пачка не уходит, поэтому
     * «выбросил больше, чем было» списывает остаток целиком, а в историю идёт то, что **ушло на
     * самом деле** — разница остатков до и после (PLAN D7).
     */
    fun dispose(
        amount: Quantity,
        movementId: Uuid,
        at: Instant,
        reason: StockMovement.Disposal.Reason = StockMovement.Disposal.Reason.OTHER,
        note: String? = null
    ): PackageAfter {
        val left = quantity.minusOrZero(amount)
        return after(left, StockMovement.Disposal(movementId, ref, quantity - left, reason, at, at, note))
    }

    /**
     * Пересчёт: «пересчитал и увидел столько» — замена значения, а не дельта (E1). Единица та же:
     * смена единицы — отдельный сценарий (D3).
     */
    fun correctTo(actual: Quantity, movementId: Uuid, at: Instant, note: String? = null): PackageAfter {
        require(actual.unit == quantity.unit) {
            "пересчёт не меняет единицу: это отдельный сценарий"
        }
        return after(actual, StockMovement.Recount(movementId, ref, quantity, actual, at, at, note))
    }

    /**
     * Человек выбросил коробку целиком (ТЗ 4.1.1.3.5). Это утилизация всего остатка, и объясняется
     * она так же: без её следа «истрачено за период» не сошлось бы — остаток исчез бы, никем не
     * принятый и ничем не объяснённый (PLAN H6).
     */
    fun thrownOut(
        movementId: Uuid,
        at: Instant,
        reason: StockMovement.Disposal.Reason = StockMovement.Disposal.Reason.OTHER,
        note: String? = null
    ): PackageEnding = when (val after = dispose(quantity, movementId, at, reason, note)) {
        is PackageAfter.Ended -> after.ending
        is PackageAfter.Left -> error("выброшенная целиком коробка не остаётся: ${after.pkg}")
    }

    /**
     * Пересчитали и увидели ноль: коробки не осталось, а «было столько» объясняет пересчёт — без
     * него остаток пропал бы из учёта без объяснения (PLAN D7, H6).
     */
    fun recountedToZero(movementId: Uuid, at: Instant, note: String? = null): PackageEnding =
        when (val after = correctTo(Quantity.zero(quantity.unit), movementId, at, note)) {
            is PackageAfter.Ended -> after.ending
            is PackageAfter.Left -> error("пересчитанная в ноль коробка не остаётся: ${after.pkg}")
        }

    /**
     * Пачки нет на сервере, и нет по нашей же причине — мы сами её туда и отправили удалять либо
     * израсходовали до конца. О количестве это не говорит ничего, поэтому следа нет (PLAN D7).
     */
    fun goneOnServer(): PackageEnding = PackageEnding(this, trace = null)

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
    fun lost(movementId: Uuid, at: Instant): PackageEnding =
        PackageEnding(this, StockMovement.AccessLoss(movementId, ref, quantity, observedAt = at))

    /**
     * Чем кончился переход: пустой коробки не бывает, поэтому ушедшая в ноль кончается, а [trace]
     * объясняет, куда делся её остаток. У оставшейся след тот же — он о том, что произошло, а не о
     * том, чем это кончилось.
     */
    private fun after(left: Quantity, trace: StockMovement?): PackageAfter =
        if (left.isZero) PackageAfter.Ended(PackageEnding(this, trace))
        else PackageAfter.Left(changed(quantity = left), trace)

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
