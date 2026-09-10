package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import java.math.BigDecimal
import kotlin.uuid.Uuid

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

fun storedQuantity(amount: String, unitId: Uuid): Quantity =
    Quantity(BigDecimal(amount), unitId)

fun storedDose(amount: String, unitId: Uuid): Dose = Dose(storedQuantity(amount, unitId))
