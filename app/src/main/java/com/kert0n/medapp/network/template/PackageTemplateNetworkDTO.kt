package com.kert0n.medapp.network.template

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Карточка справочника (`DrugTemplateDTO`) — заготовка сведений новой пачки. Количества и срока
 * годности в ней нет: для конкретной пачки их взять неоткуда (PLAN B4).
 */
@Serializable
data class PackageTemplateNetworkDTO(
    val id: Uuid,
    val name: String,
    val nameLat: String? = null,
    val activeSubstance: String? = null,
    @SerialName("formTypeId") val formId: Uuid? = null,
    val category: String? = null,
    @SerialName("quantityUnitId") val unitId: Uuid? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
)
