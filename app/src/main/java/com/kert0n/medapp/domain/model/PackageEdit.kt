package com.kert0n.medapp.domain.model

import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Описательные сведения об упаковке целиком — аргумент [Package.describe].
 *
 * **Это доменная величина, а не DTO.** Направлений у DTO три, и ни одно из них не здесь: форма
 * провода живёт в `data/remote/dto`, форма строки базы — в `data/local/entity`, состояние экрана
 * с несохранённым вводом — в своём `feature`. `PackageEdit` — то, чем домен принимает решение
 * «пачка теперь описана так»: у него есть инварианты (границы длин), и он ничего не знает ни про
 * PATCH, ни про колонки, ни про поля ввода.
 *
 * `null` означает «сведений нет», включая очистку: редактор загружает состояние целиком и
 * сохраняет целиком, поэтому `expiresOn = null` очищает срок, а `price = null` — цену. Отдельный
 * трёхвариантный тип домену не нужен — nullable-типов Kotlin достаточно, а противоположный смысл
 * `null` на проводе переводит маппер (PLAN D3).
 *
 * Количества здесь нет: оно меняется отдельным экраном пересчёта, а смена единицы — отдельный
 * сценарий без автоматической конверсии (PLAN D3, H3 №8, №9).
 */
data class PackageEdit(
    val name: String,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?,
    val expiresOn: LocalDate?,
    val defaultIntakeAmount: Quantity?,
    val note: String?,
    val price: Money?,
    val purchasedOn: LocalDate?,
    val openedOn: LocalDate?
) {
    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "PackageEdit.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageEdit.category")
        requireOptionalText(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackageEdit.manufacturer"
        )
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageEdit.country")
        requireOptionalText(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "PackageEdit.description")
        requireOptionalText(note, PACKAGE_NOTE_MAX_LENGTH, "PackageEdit.note")
    }
}
