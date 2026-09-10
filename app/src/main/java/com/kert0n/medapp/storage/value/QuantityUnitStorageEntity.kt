package com.kert0n.medapp.storage.value

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.value.QuantityUnit
import kotlin.uuid.Uuid

/**
 * Словарь единиц с **серверными** идентификаторами: их придумывает не устройство, поэтому
 * ключ приезжает снаружи, а не генерируется вставкой (PLAN F1).
 */
@Entity(tableName = "quantity_units")
class QuantityUnitStorageEntity(
    @PrimaryKey val id: Uuid,
    val name: String
) {
    fun toDomain(): QuantityUnit = QuantityUnit(id = id, name = name)
}

fun QuantityUnit.toStorageEntity(): QuantityUnitStorageEntity =
    QuantityUnitStorageEntity(id = id, name = name)
