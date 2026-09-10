package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Упаковка — конкретная пачка или флакон, а не «лекарство вообще». Одинаковые названия не
 * объединяют пачки: покупка другой пачки не пополняет старую (PLAN C0).
 *
 * **Сущность:** пачка, из которой приняли таблетку, — та же самая пачка. Тождество — [id],
 * равенство по нему, и `data class` здесь был бы неверен: он утверждает, что смена поля даёт
 * другой объект. Состояние меняют только переходы ниже; конструктор публичный и проверяет всё,
 * что верно про пачку всегда, а когда её позволено завести, решает сценарий добавления, а не
 * модель.
 *
 * [quantity] — **подтверждённый** остаток (PLAN E1), не «сколько видно на экране»: проекция
 * незакрытых намерений очереди считается отдельно и здесь не хранится.
 *
 * **Обвязки синхронизации здесь нет.** Версия предусловия, версия картины броней и момент
 * последней сверки нужны только хранению и сети; домен их не толкует, и правила о них не
 * формулируются. Они живут в `PackageSyncState` слоя данных (PLAN B3, E1).
 */
class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKitId: Uuid,
    val facts: PackageFacts,
    val quantity: Quantity,
    val addedAt: Instant,         // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid? = null, // из какой карточки справочника заполнено
    val claims: Claims? = null,   // null у неопубликованной аптечки
    val lifecycle: PackageLifecycle = PackageLifecycle.ACTIVE,
    val access: PackageAccess = PackageAccess.AVAILABLE
) {

    init {
        require(lifecycle != PackageLifecycle.ACTIVE || !quantity.isZero) {
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
     * Расход: приём, плановый или разовый.
     *
     * Нехватка бросает — через [Quantity.minus], — потому что списание в минус запрещено
     * (PLAN D5). Пачка, израсходованная до нуля, архивируется, а не удаляется: строка остаётся,
     * и приёмы с движениями продолжают читаться по ней (PLAN D3).
     */
    fun consume(amount: Quantity): Package {
        requireUsable("расход")
        require(!amount.isZero) { "расход нулевого количества не является приёмом" }
        return withQuantity(quantity - amount)
    }

    /**
     * Пересчёт: «пересчитал и увидел столько».
     *
     * Замена значения, а не дельта (PLAN E1), поэтому фактический остаток может оказаться и
     * больше прежнего. Единица обязана совпадать: смена единицы — отдельный сценарий пересчёта
     * без автоматической конверсии (PLAN D3), а не побочный эффект исправления числа.
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
     * Перенос в другую аптечку.
     *
     * Меняется только принадлежность. Что делать с предусловием и бронями при переносе через
     * границу публикации, решает сценарий переноса (PLAN E6): домен не знает, опубликована ли
     * целевая аптечка, и притворяться, что знает, здесь нельзя.
     */
    fun moveTo(medKitId: Uuid): Package {
        requireUsable("перенос")
        require(medKitId != this.medKitId) { "пачка уже лежит в этой аптечке" }
        return changed(medKitId = medKitId)
    }

    /**
     * Утилизация или удаление человеком. Идемпотентно: повторное нажатие не ошибка.
     *
     * Доступ при этом не трогается: человек вправе убрать из списка и ту пачку, до которой мы
     * больше не достаём, и потерять доступ к уже выброшенной — оба факта остаются записанными.
     */
    fun archive(): Package =
        if (lifecycle == PackageLifecycle.ARCHIVED) this
        else changed(lifecycle = PackageLifecycle.ARCHIVED)

    /**
     * Доступ утрачен: вышли из аптечки, унесли её или удалили. Идемпотентно.
     *
     * Брони снимаются: сервер снимает их каскадом по участию (PLAN D5), и держать их снимок
     * значило бы показывать чужие брони на пачке, которой у нас больше нет. Жизненный цикл не
     * трогается — выбросить пачку и потерять к ней доступ можно в любом порядке.
     */
    fun loseAccess(): Package =
        if (access == PackageAccess.LOST) this
        else changed(access = PackageAccess.LOST, claims = null)

    private fun withQuantity(left: Quantity): Package = changed(
        quantity = left,
        lifecycle = if (left.isZero) PackageLifecycle.ARCHIVED else lifecycle
    )

    /**
     * Пересчёт не оживляет архив, а недоступную пачку не правят.
     *
     * `ARCHIVED` означает «израсходована, утилизирована или удалена человеком»: отменять
     * осознанное удаление новым числом нельзя. Возврат из архива, если понадобится, будет
     * отдельным явным действием со своим подтверждением, а не следствием пересчёта.
     */
    private fun requireUsable(action: String) {
        check(lifecycle == PackageLifecycle.ACTIVE) {
            "$action недоступен для пачки в состоянии $lifecycle"
        }
        check(access == PackageAccess.AVAILABLE) {
            "$action недоступен: доступ к пачке утрачен"
        }
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * [id] и [addedAt] в списке отсутствуют — тождество и момент появления пачки не меняются
     * никогда. Значения по умолчанию берутся из текущего состояния, поэтому явный `claims = null`
     * очищает брони, а непереданный аргумент их сохраняет.
     */
    private fun changed(
        medKitId: Uuid = this.medKitId,
        facts: PackageFacts = this.facts,
        quantity: Quantity = this.quantity,
        templateId: Uuid? = this.templateId,
        claims: Claims? = this.claims,
        lifecycle: PackageLifecycle = this.lifecycle,
        access: PackageAccess = this.access
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
}
