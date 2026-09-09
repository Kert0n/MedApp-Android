package com.kert0n.medapp.domain.model

import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

const val PACKAGE_NAME_MAX_LENGTH = 300
const val PACKAGE_CATEGORY_MAX_LENGTH = 200
const val PACKAGE_MANUFACTURER_MAX_LENGTH = 300
const val PACKAGE_COUNTRY_MAX_LENGTH = 100
const val PACKAGE_DESCRIPTION_MAX_LENGTH = 4000
const val PACKAGE_NOTE_MAX_LENGTH = 200

enum class PackageStatus {
    ACTIVE,
    ARCHIVED,       // израсходована, утилизирована или удалена человеком
    INACCESSIBLE    // была общей, доступ утрачен: вышли из аптечки, унесли, удалили
}

/**
 * Упаковка — конкретная пачка или флакон, а не «лекарство вообще». Одинаковые названия не
 * объединяют пачки: покупка другой пачки не пополняет старую (PLAN C0).
 *
 * [quantity] — **подтверждённый** остаток, последний согласованный с сервером или записанный
 * локальной командой (PLAN E1). Это не «сколько сейчас видно на экране»: проекция незакрытых
 * намерений очереди считается отдельно и здесь не хранится, иначе она разъехалась бы с очередью.
 *
 * Часть полей знает сервер, часть — только устройство (PLAN C0). Граница проведена в
 * [PackageWireFields]: локальные поля не уезжают физически, а не по договорённости.
 */
data class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKitId: Uuid,

    // ——— знает сервер ———
    val name: String,
    val quantity: Quantity,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?,

    // ——— знает только устройство ———
    val expiresOn: LocalDate?,
    val defaultIntakeAmount: Quantity?, // личная подсказка для внепланового приёма, не доза курса
    val note: String?,
    val price: Money?,                  // цена всей пачки
    val purchasedOn: LocalDate?,
    val openedOn: LocalDate?,
    val addedAt: Instant,               // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid?,              // из какой карточки справочника заполнено

    val version: Long?,                 // null, пока на сервере не создана
    val claims: Claims?,                // null у неопубликованной аптечки
    val status: PackageStatus,
    val syncedAt: Instant?
) {

    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "Package.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "Package.category")
        requireOptionalText(manufacturer, PACKAGE_MANUFACTURER_MAX_LENGTH, "Package.manufacturer")
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "Package.country")
        requireOptionalText(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "Package.description")
        requireOptionalText(note, PACKAGE_NOTE_MAX_LENGTH, "Package.note")
        require(version == null || version >= 0) { "версия пачки не бывает отрицательной" }
        // Подсказка — это «сколько я обычно принимаю из ЭТОЙ пачки»: величина в чужой единице
        // не подставится в форму приёма и молча притворилась бы подходящей.
        require(defaultIntakeAmount == null || defaultIntakeAmount.unitId == quantity.unitId) {
            "доза-подсказка измеряется той же единицей, что остаток пачки"
        }
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
