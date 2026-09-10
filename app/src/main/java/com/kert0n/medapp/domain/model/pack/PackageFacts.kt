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
    val expiresOn: ExpiryDate? = null,
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
     * Считает не пачка: у [ExpiryDate] спрашивают и здесь, и в проекции доступного, у которой на
     * руках лежит один срок и никакой пачки (PLAN D4). Пачке остаётся ответить за отсутствующий
     * срок — про него мы не знаем ничего, и просроченным он не бывает.
     */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isExpiredOn(date) == true

    /** Истекает ли срок не позже чем через [days] дней, считая [date] включительно (D8). */
    fun expiresWithin(date: LocalDate, days: Long): Boolean =
        expiresOn?.expiresWithin(date, days) == true
}
