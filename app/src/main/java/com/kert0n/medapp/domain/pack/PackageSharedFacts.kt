package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText

/**
 * Та часть сведений об упаковке, что уезжает на сервер: описание препарата, а не этой коробки
 * (PLAN C0). «Правка была только локальной» — это `before.shared == after.shared`.
 */
data class PackageSharedFacts(
    val name: String,
    val form: DosageForm? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
) {
    init {
        requireText(name, NAME_MAX_LENGTH, "PackageSharedFacts.name")
        requireOptionalText(category, CATEGORY_MAX_LENGTH, "PackageSharedFacts.category")
        requireOptionalText(
            manufacturer,
            MANUFACTURER_MAX_LENGTH,
            "PackageSharedFacts.manufacturer"
        )
        requireOptionalText(country, COUNTRY_MAX_LENGTH, "PackageSharedFacts.country")
        requireOptionalText(
            description,
            DESCRIPTION_MAX_LENGTH,
            "PackageSharedFacts.description"
        )
    }

    companion object {
        const val NAME_MAX_LENGTH = 300
        const val CATEGORY_MAX_LENGTH = 200
        const val MANUFACTURER_MAX_LENGTH = 300
        const val COUNTRY_MAX_LENGTH = 100
        const val DESCRIPTION_MAX_LENGTH = 4000
    }
}
