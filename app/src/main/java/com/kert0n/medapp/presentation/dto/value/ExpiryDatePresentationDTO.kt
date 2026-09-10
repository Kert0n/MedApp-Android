package com.kert0n.medapp.presentation.dto.value

/**
 * Срок годности одной строкой: «03.2027», «31.03.2027», «2027-03-31».
 *
 * Одно поле, а не год с месяцем порознь: на упаковке напечатана именно строка, и человек
 * перепечатывает её как есть. Что месяц означает его последний день — правило домена
 * ([com.kert0n.medapp.domain.model.pack.ExpiryDate]), а не этой формы: маппер отдаёт домену
 * готовое значение срока, а не дату.
 */
data class ExpiryDatePresentationDTO(val text: String)
