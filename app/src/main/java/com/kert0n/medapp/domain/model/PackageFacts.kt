package com.kert0n.medapp.domain.model

import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Описательные сведения об упаковке — то, что человек про неё знает и правит.
 *
 * **Часть пачки, а не мешок аргументов.** Границы длин объявлены здесь один раз, и [Package] их
 * не переобъявляет: пока сведения были отдельным носителем для фабрики, одни и те же шесть
 * проверок стояли в трёх местах и расходились бы молча.
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
    val name: String,
    val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val expiresOn: LocalDate? = null,
    val defaultIntakeAmount: Quantity? = null,
    val note: String? = null,
    val price: Money? = null,
    val purchasedOn: LocalDate? = null,
    val openedOn: LocalDate? = null
) {
    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "PackageFacts.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageFacts.category")
        requireOptionalText(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackageFacts.manufacturer"
        )
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageFacts.country")
        requireOptionalText(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "PackageFacts.description")
        requireOptionalText(note, PACKAGE_NOTE_MAX_LENGTH, "PackageFacts.note")
    }

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
