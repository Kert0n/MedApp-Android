package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import kotlin.uuid.Uuid

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
        amount?.let { requireNetworkAmount(it, "PackagePatchNetworkDTO.amount") }
        require(name == null || name.length <= PackageSharedFacts.NAME_MAX_LENGTH) {
            "PackagePatchNetworkDTO.name: длиннее ${PackageSharedFacts.NAME_MAX_LENGTH} символов"
        }
        requireClearable(
            category,
            PackageSharedFacts.CATEGORY_MAX_LENGTH,
            "PackagePatchNetworkDTO.category"
        )
        requireClearable(
            manufacturer,
            PackageSharedFacts.MANUFACTURER_MAX_LENGTH,
            "PackagePatchNetworkDTO.manufacturer"
        )
        requireClearable(
            country,
            PackageSharedFacts.COUNTRY_MAX_LENGTH,
            "PackagePatchNetworkDTO.country"
        )
        requireClearable(
            description,
            PackageSharedFacts.DESCRIPTION_MAX_LENGTH,
            "PackagePatchNetworkDTO.description"
        )
    }

    val isEmpty: Boolean
        get() = name == null && amount == null && unitId == null && formId == null &&
                category == null && manufacturer == null && country == null && description == null
}
