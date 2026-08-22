package com.kert0n.medapp.data.remote.dto

import kotlinx.serialization.Serializable

/** Упаковка: остаток и описательные поля. Единица и форма — только идентификаторами. */
@Serializable
data class DrugDTO(
    val id: String,
    val name: String,
    /** Текущий остаток, `"100.000000"`. Ноль законен: пачку допили. */
    val quantity: String,
    val quantityUnitId: String,
    val formTypeId: String? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val medKitId: String,
    /** Версия состояния самой пачки — её и предъявляют команды упаковки. */
    val version: Long
)

/**
 * Что заявлено на упаковку бронями.
 *
 * Две величины, а не одна: сумма говорит, разобрана ли пачка, своя доля — что показать
 * владельцу. Сумма может превышать остаток, и это не ошибка.
 */
@Serializable
data class ReservationsDTO(
    val total: String,
    /** Отсутствует, когда вызывающий ничего не заявлял. */
    val mine: String? = null,
    /** Версия всей картины броней на этой пачке — отдельная от версии самой пачки. */
    val version: Long
)

/**
 * Упаковка вместе с тем, что на неё заявлено.
 *
 * Два состояния разделены намеренно: пачка отвечает за себя, брони меняются от чужих действий.
 */
@Serializable
data class DrugSnapshotDTO(
    val drug: DrugDTO,
    val reservations: ReservationsDTO
)

/** Завести упаковку в аптечке; аптечка задаётся путём, поэтому её идентификатора в теле нет. */
@Serializable
data class DrugCreateRequest(
    val name: String,
    /** Строго больше нуля. */
    val quantity: String,
    val quantityUnitId: String,
    val formTypeId: String? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
)

/**
 * Частичная правка: `null` значит «не трогать это поле».
 *
 * Очистить необязательное поле этим запросом нельзя — отличить «не передал» от «передал пустое»
 * в такой схеме невозможно.
 */
@Serializable
data class DrugPatchRequest(
    val name: String? = null,
    /** Исправление пересчётом, а не пополнение: новая пачка — новая упаковка. */
    val quantity: String? = null,
    val quantityUnitId: String? = null,
    val formTypeId: String? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val version: Long? = null
)

/** Запись справочника Vidal: образец, с которого заводят упаковку. Только для чтения. */
@Serializable
data class DrugTemplateDTO(
    val id: String,
    val name: String,
    val nameLat: String? = null,
    val activeSubstance: String? = null,
    val formTypeId: String? = null,
    val category: String? = null,
    val quantityUnitId: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
)

/** Запись общего словаря — единица измерения или форма выпуска. */
@Serializable
data class VocabularyEntryDTO(
    val id: String,
    val name: String
)
