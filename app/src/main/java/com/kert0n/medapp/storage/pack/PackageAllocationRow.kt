package com.kert0n.medapp.storage.pack

import androidx.room.ColumnInfo
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.storage.value.storedDose
import kotlin.uuid.Uuid

/**
 * Сколько этой пачки занято активным курсом. Пачка входит не больше чем в один активный курс,
 * поэтому строка на пачку одна, а перевод доз в единицы пачки делает доза курса (PLAN D5).
 */
class PackageAllocationRow(
    @ColumnInfo(name = "package_id") val packageId: Uuid,
    @ColumnInfo(name = "allocated_doses") val allocatedDoses: Int,
    @ColumnInfo(name = "dose_amount") val doseAmount: String,
    @ColumnInfo(name = "unit_id") val unitId: Uuid
) {
    val allocated: Quantity get() = storedDose(doseAmount, unitId) * Doses(allocatedDoses)
}
