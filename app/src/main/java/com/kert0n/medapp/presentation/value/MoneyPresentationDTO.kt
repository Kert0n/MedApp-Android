package com.kert0n.medapp.presentation.value

/** Цена: сумма строкой и код валюты, как их выбрали на экране. */
data class MoneyPresentationDTO(val amount: String, val currencyCode: String = "RUB")
