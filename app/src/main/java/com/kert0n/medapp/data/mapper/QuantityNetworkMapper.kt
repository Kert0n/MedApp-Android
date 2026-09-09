package com.kert0n.medapp.data.mapper

import com.kert0n.medapp.domain.model.Money
import com.kert0n.medapp.domain.model.Quantity

/**
 * Десятичная строка для провода (PLAN B2): без экспоненты и без знака.
 *
 * Формат объявляет сетевой слой, а не величина. Сервер хранит `numeric(19, 6)` и принимает
 * количества строками, потому что в `Double` такое число не помещается; изменится контракт —
 * изменится этот файл, а `Quantity` останется прежним.
 */
fun Quantity.toNetworkAmount(): String = amount.toPlainString()

/**
 * Цена на сервер не уезжает вовсе (PLAN C0), поэтому у неё нет проводного представления.
 * Функция существует для отчётов и обмена внутри устройства, где нужна та же десятичная строка.
 */
fun Money.toDecimalString(): String = amount.toPlainString()
