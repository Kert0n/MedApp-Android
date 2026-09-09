package com.kert0n.medapp.data.remote.dto

import com.kert0n.medapp.domain.model.PACKAGE_CATEGORY_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_COUNTRY_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_DESCRIPTION_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_MANUFACTURER_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_NAME_MAX_LENGTH
import com.kert0n.medapp.domain.model.Quantity
import kotlin.uuid.Uuid

/**
 * Поля упаковки, уезжающие на сервер: ровно то, что принимает создание пачки, и ничего сверх.
 *
 * Локальные сведения — срок годности, доза-подсказка, заметка, цена, даты покупки и вскрытия —
 * сюда не попадают **физически**, а не по договорённости. Список полей здесь и список на экране
 * последствий публикации (PLAN E5) — одно и то же.
 *
 * Живёт в сетевом слое, а не в домене: это форма провода. Величины пока доменные (`Quantity`);
 * превращение в десятичные строки и `@Serializable` придут вместе с настоящим контрактом в PR 5.
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
        requireWireValue(name, PACKAGE_NAME_MAX_LENGTH, "PackageWireFields.name")
        requireOptionalWireValue(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageWireFields.category")
        requireOptionalWireValue(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackageWireFields.manufacturer"
        )
        requireOptionalWireValue(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageWireFields.country")
        requireOptionalWireValue(
            description,
            PACKAGE_DESCRIPTION_MAX_LENGTH,
            "PackageWireFields.description"
        )
    }
}

/**
 * Намерение PATCH: `null` — не изменять, текст `""` — очистить.
 *
 * Это **не** семантика локальной формы. У текущего PATCH и отсутствие поля, и `null` означают
 * «не трогать», поэтому очистка выражается пустой строкой (PLAN D3, H2). В доменной форме
 * `PackageEdit` тот же `null` значит ровно обратное — «сведений нет», — и именно поэтому тип
 * отдельный и лежит в другом слое: одно поле с двумя противоположными смыслами `null` рано или
 * поздно прочитали бы не по той стороне границы.
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
        require(name == null || name.length <= PACKAGE_NAME_MAX_LENGTH) {
            "PackageWireEdit.name: длиннее $PACKAGE_NAME_MAX_LENGTH символов"
        }
        requireClearable(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageWireEdit.category")
        requireClearable(manufacturer, PACKAGE_MANUFACTURER_MAX_LENGTH, "PackageWireEdit.manufacturer")
        requireClearable(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageWireEdit.country")
        requireClearable(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "PackageWireEdit.description")
    }

    val isEmpty: Boolean
        get() = name == null && quantity == null && unitId == null && formId == null &&
                category == null && manufacturer == null && country == null && description == null
}

private fun requireWireValue(value: String, maxLength: Int, field: String) {
    require(value.isNotBlank()) { "$field: пустое значение не является сведением" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}

private fun requireOptionalWireValue(value: String?, maxLength: Int, field: String) {
    if (value != null) requireWireValue(value, maxLength, field)
}

/** Пустая строка здесь законна — это очистка. Пробелы не значат ни того, ни другого. */
private fun requireClearable(value: String?, maxLength: Int, field: String) {
    if (value == null || value.isEmpty()) return
    require(value.isNotBlank()) { "$field: пробелы не являются ни значением, ни очисткой" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}
