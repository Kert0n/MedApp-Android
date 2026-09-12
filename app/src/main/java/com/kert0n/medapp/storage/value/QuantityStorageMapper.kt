package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
import java.math.BigDecimal

/**
 * Количество хранится десятичной строкой и единицей в отдельной колонке: `REAL` потерял бы
 * разряды, а «минимальные единицы» потребовали бы знать масштаб каждой единицы (PLAN F3).
 */
fun Quantity.toStorageAmount(): String = amount.toPlainString()

/**
 * Ключ сортировки: та же строка, дополненная нулями до предельной ширины величины, поэтому
 * `ORDER BY` по ней совпадает с числовым порядком без `CAST(… AS REAL)`. Сравнивать её можно
 * только внутри одной единицы — величины разных единиц не упорядочены вовсе.
 */
fun Quantity.toStorageSortKey(): String {
    val fixed = amount.setScale(Quantity.SCALE).toPlainString()
    val point = fixed.indexOf('.')
    return fixed.substring(0, point).padStart(Quantity.MAX_INTEGER_DIGITS, '0') +
        fixed.substring(point)
}

/** Единица приходит объектом: колонка держит её идентификатор, а объект даёт снимок словаря. */
fun storedQuantity(amount: String, unit: QuantityUnit): Quantity = Quantity(BigDecimal(amount), unit)

fun storedDose(amount: String, unit: QuantityUnit): Dose = Dose(storedQuantity(amount, unit))
