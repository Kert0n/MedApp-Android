package com.kert0n.medapp.domain.model

import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Полное сохраняемое состояние описательной формы упаковки: и серверные поля, и локальные.
 *
 * `null` означает «сведений нет», включая очистку. Это обычные nullable-типы Kotlin, а не
 * закодированная команда PATCH: редактор загружает состояние целиком и сохраняет целиком, поэтому
 * `expiresOn = null` очищает срок, а `price = null` — цену. Отдельный трёхвариантный тип
 * («не менять» / «очистить» / «значение») локальному редактору не нужен, а семантика PATCH
 * остаётся на сетевой границе (PLAN D3).
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

/**
 * Поля упаковки, уезжающие на сервер: ровно то, что принимает создание пачки, и ничего сверх.
 * Локальные поля сюда не попадают физически, а не по договорённости (PLAN D3, E5).
 */
data class PackageWireFields(
    val name: String,
    val quantity: Quantity,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?
) {
    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "PackageWireFields.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageWireFields.category")
        requireOptionalText(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackageWireFields.manufacturer"
        )
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageWireFields.country")
        requireOptionalText(
            description,
            PACKAGE_DESCRIPTION_MAX_LENGTH,
            "PackageWireFields.description"
        )
    }
}

/**
 * Сетевое намерение PATCH: `null` — не изменять, текст `""` — очистить.
 *
 * Это **не** семантика локальной формы: у текущего PATCH и отсутствие поля, и `null` означают
 * «не трогать», поэтому очистка выражается пустой строкой (PLAN D3, H2).
 */
data class PackageWireEdit(
    val name: String? = null,
    val quantity: Quantity? = null,
    val unitId: Uuid? = null,
    val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
) {
    init {
        require(name == null || name.isNotBlank()) { "название нельзя очистить" }
        requireWireText(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageWireEdit.category")
        requireWireText(manufacturer, PACKAGE_MANUFACTURER_MAX_LENGTH, "PackageWireEdit.manufacturer")
        requireWireText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageWireEdit.country")
        requireWireText(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "PackageWireEdit.description")
        require(name == null || name.length <= PACKAGE_NAME_MAX_LENGTH) {
            "PackageWireEdit.name: длиннее $PACKAGE_NAME_MAX_LENGTH символов"
        }
    }

    val isEmpty: Boolean
        get() = name == null && quantity == null && unitId == null && formId == null &&
                category == null && manufacturer == null && country == null && description == null
}

/**
 * Что из локальной правки уезжает на сервер и что уехать не может.
 *
 * [formIdClearUnsupported] — не мелочь интерфейса. Очистить UUID формы серверным PATCH сейчас
 * нельзя: `null` значит «не менять», а `""` не является UUID. Локально форму убрать можно, и
 * тогда экран обязан объяснить ограничение — иначе неудалённая серверная форма выдавалась бы за
 * очищенную (PLAN D3).
 */
data class PackageWirePatch(
    val edit: PackageWireEdit?,          // null — серверных полей не изменилось
    val formIdClearUnsupported: Boolean
)

/**
 * Сравнивает сохранённую форму с тем, что известно о пачке, и оставляет только изменившееся.
 *
 * Отправлять поле, которое человек не трогал, нельзя: PATCH перетёр бы им чужую правку тем же
 * самым значением. Количество и единица не заполняются здесь вовсе — это отдельные сценарии
 * пересчёта (PLAN D3, E1).
 */
fun PackageEdit.wirePatchFrom(current: Package): PackageWirePatch {
    val edit = PackageWireEdit(
        name = name.takeIf { it != current.name },
        formId = formId.takeIf { it != null && it != current.formId },
        category = wireText(current.category, category),
        manufacturer = wireText(current.manufacturer, manufacturer),
        country = wireText(current.country, country),
        description = wireText(current.description, description)
    )
    val formCleared = current.formId != null && formId == null
    return PackageWirePatch(
        edit = edit.takeIf { !it.isEmpty },
        formIdClearUnsupported = formCleared && current.version != null
    )
}

private fun wireText(current: String?, saved: String?): String? = when {
    saved == current -> null   // не изменилось — не трогаем
    saved == null -> ""        // очищено — на проводе это пустая строка
    else -> saved
}

private fun requireWireText(value: String?, maxLength: Int, field: String) {
    if (value == null || value.isEmpty()) return
    require(value.isNotBlank()) { "$field: пробелы не являются ни значением, ни очисткой" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}
