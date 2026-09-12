package com.kert0n.medapp.storage.medkit

import androidx.room.ColumnInfo
import kotlin.uuid.Uuid

/** Сколько живых упаковок лежит в аптечке и сколько из них просрочено — одним запросом на все. */
class MedKitContentsStorageRow(
    @ColumnInfo(name = "med_kit_id") val medKitId: Uuid,
    val packages: Int,
    val expired: Int
)
