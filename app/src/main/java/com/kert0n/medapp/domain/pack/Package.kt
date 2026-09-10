package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Упаковка — конкретная пачка или флакон; одинаковые названия пачки не объединяют (PLAN C0).
 * Сущность: пачка, из которой приняли таблетку, — та же пачка, равенство по [id], состояние
 * меняют переходы. [quantity] — подтверждённый остаток (E1); обвязка синхронизации живёт в
 * `PackageSyncState` слоя данных.
 */
class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKitId: Uuid,
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
        require(hint == null || hint.unitId == quantity.unitId) {
            "доза-подсказка измеряется той же единицей, что остаток пачки"
        }
    }

    val name: String get() = facts.name

    fun isExpiredOn(date: LocalDate): Boolean = facts.isExpiredOn(date)

    fun expiresWithin(date: LocalDate, days: Long): Boolean = facts.expiresWithin(date, days)

    /**
     * Расход — приём, плановый или разовый. В минус не списывает (PLAN D5); пачка,
     * израсходованная до нуля, архивируется.
     */
    fun consume(amount: Quantity): Package {
        requireUsable("расход")
        require(!amount.isZero) { "расход нулевого количества не является приёмом" }
        return withQuantity(quantity - amount)
    }

    /**
     * Пересчёт: «пересчитал и увидел столько» — замена значения, а не дельта (E1). Единица та же:
     * смена единицы — отдельный сценарий (D3).
     */
    fun correctTo(actual: Quantity): Package {
        requireUsable("пересчёт")
        require(actual.unitId == quantity.unitId) {
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
     * Перенос в другую аптечку меняет только принадлежность; что делать с бронями на границе
     * публикации, решает сценарий переноса (PLAN E6).
     */
    fun moveTo(medKitId: Uuid): Package {
        requireUsable("перенос")
        require(medKitId != this.medKitId) { "пачка уже лежит в этой аптечке" }
        return changed(medKitId = medKitId)
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
        medKitId: Uuid = this.medKitId,
        facts: PackageFacts = this.facts,
        quantity: Quantity = this.quantity,
        templateId: Uuid? = this.templateId,
        claims: Claims? = this.claims,
        lifecycle: Lifecycle = this.lifecycle,
        access: Access = this.access
    ): Package = Package(
        id = id,
        medKitId = medKitId,
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
