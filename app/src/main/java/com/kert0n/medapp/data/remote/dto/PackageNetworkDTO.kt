package com.kert0n.medapp.data.remote.dto

import com.kert0n.medapp.domain.model.PACKAGE_CATEGORY_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_COUNTRY_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_DESCRIPTION_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_MANUFACTURER_MAX_LENGTH
import com.kert0n.medapp.domain.model.PACKAGE_NAME_MAX_LENGTH
import com.kert0n.medapp.domain.model.requireOptionalText
import com.kert0n.medapp.domain.model.requireText
import kotlin.uuid.Uuid

/**
 * Поля упаковки, уезжающие на сервер: ровно то, что принимает создание пачки, и ничего сверх.
 *
 * Локальные сведения — срок годности, доза-подсказка, заметка, цена, даты покупки и вскрытия —
 * сюда не попадают **физически**, а не по договорённости. Список полей здесь и список на экране
 * последствий публикации (PLAN E5) — одно и то же.
 *
 * Живёт в сетевом слое, а не в домене: это форма провода, и величины в ней уже проводные.
 * Количество — десятичная строка по шаблону B2 и отдельный идентификатор единицы, потому что
 * именно это принимает сервер; доменный `Quantity` в тело запроса не попадает. `@Serializable`
 * и точные имена полей придут вместе с настоящим контрактом в PR 5.
 */
data class PackagePostNetworkDTO(
    val name: String,
    val amount: String,
    val unitId: Uuid,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?
) {
    init {
        // Границы те же, что у домена: создание пачки посылает её поля как есть, и расхождение
        // между «влезло в модель» и «влезло в запрос» означало бы отказ сервера после успешного
        // сохранения.
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "PackagePostNetworkDTO.name")
        requireWireAmount(amount, "PackagePostNetworkDTO.amount")
        requireOptionalText(
            category,
            PACKAGE_CATEGORY_MAX_LENGTH,
            "PackagePostNetworkDTO.category"
        )
        requireOptionalText(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackagePostNetworkDTO.manufacturer"
        )
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackagePostNetworkDTO.country")
        requireOptionalText(
            description,
            PACKAGE_DESCRIPTION_MAX_LENGTH,
            "PackagePostNetworkDTO.description"
        )
    }
}

/**
 * Намерение PATCH: `null` — не изменять, текст `""` — очистить.
 *
 * Это **не** семантика доменных сведений. У текущего PATCH и отсутствие поля, и `null` означают
 * «не трогать», поэтому очистка выражается пустой строкой (PLAN D3, H2). В доменной форме
 * `PackageFacts` тот же `null` значит ровно обратное — «сведений нет», — и именно поэтому тип
 * отдельный и лежит в другом слое: одно поле с двумя противоположными смыслами `null` рано или
 * поздно прочитали бы не по той стороне границы.
 *
 * Количество и единица идут порознь и обе необязательны: сервер принимает их независимо, а
 * доменное `Quantity` держало бы единицу дважды — внутри величины и рядом с ней.
 */
data class PackagePatchNetworkDTO(
    val name: String? = null,
    val amount: String? = null,
    val unitId: Uuid? = null,
    val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
) {
    init {
        require(name == null || name.isNotBlank()) { "название нельзя очистить" }
        amount?.let { requireWireAmount(it, "PackagePatchNetworkDTO.amount") }
        require(name == null || name.length <= PACKAGE_NAME_MAX_LENGTH) {
            "PackagePatchNetworkDTO.name: длиннее $PACKAGE_NAME_MAX_LENGTH символов"
        }
        requireClearable(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackagePatchNetworkDTO.category")
        requireClearable(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackagePatchNetworkDTO.manufacturer"
        )
        requireClearable(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackagePatchNetworkDTO.country")
        requireClearable(
            description,
            PACKAGE_DESCRIPTION_MAX_LENGTH,
            "PackagePatchNetworkDTO.description"
        )
    }

    val isEmpty: Boolean
        get() = name == null && amount == null && unitId == null && formId == null &&
                category == null && manufacturer == null && country == null && description == null
}

/**
 * Шаблон количества на проводе (PLAN B2): не больше 13 разрядов до точки и 6 после, без знака и
 * экспоненты. Проверяется здесь, потому что обещание даёт запрос, а не величина.
 */
private val WIRE_AMOUNT = Regex("""^\d{1,13}(\.\d{1,6})?$""")

private fun requireWireAmount(amount: String, field: String) {
    require(WIRE_AMOUNT.matches(amount)) { "$field: не десятичная строка контракта B2" }
}

/**
 * Пустая строка здесь законна — это очистка, и только поэтому у PATCH своя проверка, а не
 * доменная: `requireText` пустую строку отвергает, потому что в домене она не значит ничего.
 * Пробелы не значат ни того, ни другого ни там, ни здесь.
 */
private fun requireClearable(value: String?, maxLength: Int, field: String) {
    if (value == null || value.isEmpty()) return
    require(value.isNotBlank()) { "$field: пробелы не являются ни значением, ни очисткой" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}
