package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.value.Money
import com.kert0n.medapp.domain.model.value.Quantity
import com.kert0n.medapp.domain.model.value.requireOptionalText
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Описательные сведения об упаковке целиком — то, что человек про неё знает и правит.
 *
 * **Композиция, а не плоский список.** [shared] — то, что уезжает на сервер; остальные поля
 * личные и не уезжают вовсе (PLAN C0). Пока граница жила в комментарии, каждый маппер выбирал
 * поля вручную, а «локальная ли это правка» проверялось перечислением шести имён. Теперь это
 * один вопрос к структуре, и сведения при этом остаются **одной** вещью: `PackageSharedFacts` —
 * часть упаковки, а не второй тип рядом с ней (решение PR 3, PLAN E2).
 *
 * `null` означает «сведений нет», включая очистку: редактор загружает состояние целиком и
 * сохраняет целиком, поэтому `expiresOn = null` очищает срок, а `price = null` — цену. Отдельный
 * трёхвариантный тип домену не нужен; противоположный смысл `null` на проводе переводит маппер
 * (PLAN D3).
 *
 * Количества здесь нет: оно меняется отдельным экраном пересчёта, а смена единицы — отдельный
 * сценарий без автоматической конверсии (PLAN D3, H3 №8, №9).
 */
data class PackageFacts(
    val shared: PackageSharedFacts,
    val expiresOn: LocalDate? = null,
    val defaultIntakeAmount: Quantity? = null,
    val note: String? = null,
    val price: Money? = null,
    val purchasedOn: LocalDate? = null,
    val openedOn: LocalDate? = null
) {
    init {
        requireOptionalText(note, PACKAGE_NOTE_MAX_LENGTH, "PackageFacts.note")
    }

    val name: String get() = shared.name
    val formId: Uuid? get() = shared.formId
    val category: String? get() = shared.category
    val manufacturer: String? get() = shared.manufacturer
    val country: String? get() = shared.country
    val description: String? get() = shared.description

    /**
     * Дата передаётся, а не берётся из часов: иначе свойство непроверяемо тестом.
     *
     * Сравнение именно `isBefore`: дата включительная, пачка «годна до 31 марта» просрочена
     * только 1 апреля. Правило записано здесь, чтобы знак не «поправили» при рефакторинге.
     */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isBefore(date) == true

    /**
     * Истекает ли срок в ближайшие [days] дней, считая [date] включительно.
     *
     * Именно «не позже чем через N дней», а не «ровно за N дней»: пороги 3 и 1 день (PLAN D8)
     * проверяются фоновой задачей, а она может задержаться и перепрыгнуть точную дату. Уже
     * просроченная пачка не «истекает скоро» — у неё другое состояние и другое сообщение.
     */
    fun expiresWithin(date: LocalDate, days: Long): Boolean {
        require(days >= 0) { "окно предупреждения не бывает отрицательным" }
        val expires = expiresOn ?: return false
        if (expires.isBefore(date)) return false
        return !expires.isAfter(date.plusDays(days))
    }
}
