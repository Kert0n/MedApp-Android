package com.kert0n.medapp.data.mapper.value

import com.kert0n.medapp.domain.model.value.Quantity

/**
 * Десятичная строка для провода (PLAN B2): без экспоненты и без знака.
 *
 * Формат объявляет сетевой слой, а не величина. Сервер хранит `numeric(19, 6)` и принимает
 * количества строками, потому что в `Double` такое число не помещается; изменится контракт —
 * изменится этот файл, а `Quantity` останется прежним.
 */
fun Quantity.toNetworkAmount(): String = amount.toPlainString()
