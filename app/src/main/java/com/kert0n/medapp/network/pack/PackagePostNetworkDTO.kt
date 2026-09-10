package com.kert0n.medapp.network.pack

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Создание пачки на проводе (`DrugCreateRequest`): ровно то, что принимает сервер, и ничего сверх.
 *
 * Идентификатор придумывает клиент: повтор с тем же `id` даёт 409, а не вторую пачку, поэтому
 * потерянный ответ можно повторить (PLAN B4, C0). Локальные сведения — срок годности, заметка,
 * цена — сюда не попадают **физически**: список полей здесь и на экране последствий публикации
 * (PLAN E5) один и тот же. Количество — строка B2 и отдельная единица, как их принимает сервер.
 */
@Serializable
data class PackagePostNetworkDTO(
    val id: Uuid,
    val name: String,
    @SerialName("quantity") val amount: String,
    @SerialName("quantityUnitId") val unitId: Uuid,
    @SerialName("formTypeId") val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?
) {
    init {
        // Границы те же, что у домена: расхождение между «влезло в модель» и «влезло в запрос»
        // означало бы отказ сервера после успешного сохранения.
        requireText(name, PackageSharedFacts.NAME_MAX_LENGTH, "PackagePostNetworkDTO.name")
        requirePositiveNetworkAmount(amount, "PackagePostNetworkDTO.amount")
        requireOptionalText(
            category,
            PackageSharedFacts.CATEGORY_MAX_LENGTH,
            "PackagePostNetworkDTO.category"
        )
        requireOptionalText(
            manufacturer,
            PackageSharedFacts.MANUFACTURER_MAX_LENGTH,
            "PackagePostNetworkDTO.manufacturer"
        )
        requireOptionalText(
            country,
            PackageSharedFacts.COUNTRY_MAX_LENGTH,
            "PackagePostNetworkDTO.country"
        )
        requireOptionalText(
            description,
            PackageSharedFacts.DESCRIPTION_MAX_LENGTH,
            "PackagePostNetworkDTO.description"
        )
    }
}
