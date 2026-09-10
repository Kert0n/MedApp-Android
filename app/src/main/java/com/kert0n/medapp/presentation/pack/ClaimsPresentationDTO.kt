package com.kert0n.medapp.presentation.pack

/** Строки чисел нормализованы маппером: 1 и 1.000000 дают одинаковое состояние. */
data class ClaimsPresentationDTO(val total: String, val mine: String?)
