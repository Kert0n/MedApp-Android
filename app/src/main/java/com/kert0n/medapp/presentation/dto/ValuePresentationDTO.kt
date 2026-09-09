package com.kert0n.medapp.presentation.dto

import kotlin.uuid.Uuid

/**
 * Величины в том виде, в каком их держит экран: строками, как человек напечатал.
 *
 * Это третье направление DTO — представление, рядом с сетевым (`data/remote/dto`) и хранением
 * (`data/local/entity`). Форма ввода не может держать `Quantity`: пока человек печатает, там лежит
 * «12,», «0.0000001» или пусто, и ни одно из этих состояний величиной не является. Приводит их к
 * домену маппер представления — так же, как сетевой маппер приводит домен к телу запроса.
 */
data class QuantityPresentationDTO(val amount: String, val unitId: Uuid)

/** Цена: сумма строкой и код валюты, как их выбрали на экране. */
data class MoneyPresentationDTO(val amount: String, val currencyCode: String = "RUB")

/**
 * Срок годности одной строкой: «03.2027», «31.03.2027», «2027-03-31».
 *
 * Одно поле, а не год с месяцем порознь: на упаковке напечатана именно строка, и человек
 * перепечатывает её как есть. Что месяц означает его последний день — правило домена
 * ([com.kert0n.medapp.domain.model.ExpiryDate]), а не этой формы.
 */
data class ExpiryDatePresentationDTO(val text: String)
