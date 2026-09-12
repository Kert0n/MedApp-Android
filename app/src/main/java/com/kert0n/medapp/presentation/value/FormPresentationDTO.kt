package com.kert0n.medapp.presentation.value

import com.kert0n.medapp.domain.value.DosageForm
import kotlin.uuid.Uuid

/**
 * Форма выпуска глазами экрана: номер и имя, с равенством по содержимому — по тому же доводу,
 * что и [UnitPresentationDTO]. Отдельный тип, а не общая «запись словаря»: подставить форму
 * туда, где ждут единицу, нечем (PLAN D1).
 */
data class FormPresentationDTO(val id: Uuid, val name: String)

fun DosageForm.toPresentationDTO(): FormPresentationDTO = FormPresentationDTO(id, name)
