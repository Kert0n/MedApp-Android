package com.kert0n.medapp.presentation.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.presentation.ParsedInput

/**
 * Приведение формы аптечки к тому, что требует домен: непустое название и необязательное место
 * хранения. Обрезка пробелов и «пустое поле значит не указано» — свойства ввода, и живут они
 * здесь; домен получает готовые сведения (PLAN H1).
 *
 * Пределы длин берутся у [MedKit], а не повторяются числами: правило живёт на своём типе, и
 * второй его копии в форме не заводится (AGENTS).
 */
fun MedKitFormPresentationDTO.toDomain(): ParsedInput<MedKitDescription, MedKitFormError> {
    val name = name.trim()
    val location = location.trim().takeIf { it.isNotEmpty() }
    return when {
        name.isEmpty() -> ParsedInput.Rejected(MedKitFormError.NAME_EMPTY)
        name.length > MedKit.NAME_MAX_LENGTH -> ParsedInput.Rejected(MedKitFormError.NAME_TOO_LONG)
        location != null && location.length > MedKit.LOCATION_MAX_LENGTH ->
            ParsedInput.Rejected(MedKitFormError.LOCATION_TOO_LONG)
        else -> ParsedInput.Parsed(MedKitDescription(name, location))
    }
}

/**
 * Личные сведения аптечки, прошедшие проверку. Отдельный тип, а не пара строк: сценарию нужно
 * знать, что это уже проверено, и подставить сюда сырой ввод не получится.
 */
data class MedKitDescription(val name: String, val location: String?)
