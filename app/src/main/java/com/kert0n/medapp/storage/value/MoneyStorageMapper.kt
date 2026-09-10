package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.Money
import java.math.BigDecimal
import java.util.Currency

/** Цена хранится по тому же правилу, что количество: десятичная строка и код валюты рядом. */
fun Money.toStorageAmount(): String = amount.toPlainString()

fun Money.toStorageCurrency(): String = currencyCode

fun storedMoney(amount: String, currencyCode: String): Money =
    Money(BigDecimal(amount), Currency.getInstance(currencyCode))
