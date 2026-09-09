package com.kert0n.medapp.domain.model

import kotlin.uuid.Uuid

const val VOCABULARY_NAME_MAX_LENGTH = 200

/**
 * Единица измерения из общего словаря. Идентификаторы **серверные**: словарь один на всю
 * систему, и клиент хранит его снимок (PLAN F1).
 */
data class QuantityUnit(val id: Uuid, val name: String) {
    init {
        requireText(name, VOCABULARY_NAME_MAX_LENGTH, "QuantityUnit.name")
    }
}

/**
 * Форма выпуска. Отдельный тип, а не общая «запись словаря» вместе с единицей: обёрток над
 * идентификаторами в домене нет (PLAN D1), и при одном общем типе ничто не помешало бы
 * подставить форму туда, где ждут единицу.
 */
data class DosageForm(val id: Uuid, val name: String) {
    init {
        requireText(name, VOCABULARY_NAME_MAX_LENGTH, "DosageForm.name")
    }
}
