package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.PACKAGE_CATEGORY_MAX_LENGTH
import com.kert0n.medapp.domain.pack.PACKAGE_COUNTRY_MAX_LENGTH
import com.kert0n.medapp.domain.pack.PACKAGE_DESCRIPTION_MAX_LENGTH
import com.kert0n.medapp.domain.pack.PACKAGE_MANUFACTURER_MAX_LENGTH
import com.kert0n.medapp.domain.pack.PACKAGE_NAME_MAX_LENGTH
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
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
        requireNetworkAmount(amount, "PackagePostNetworkDTO.amount")
        require(amount.any { it in '1'..'9' }) {
            "PackagePostNetworkDTO.amount: начальный остаток должен быть положительным"
        }
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
