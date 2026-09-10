package com.kert0n.medapp.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kert0n.medapp.storage.value.DosageFormStorageEntity
import com.kert0n.medapp.storage.value.QuantityUnitStorageEntity

/**
 * Локальная база приложения. Схема экспортируется в `app/schemas` и лежит в репозитории:
 * без прошлой версии рядом переход нечем проверить, а разрушающий откат запрещён — потерять
 * очередь и историю при обновлении приложения нельзя (PLAN F4).
 */
@Database(
    entities = [
        QuantityUnitStorageEntity::class,
        DosageFormStorageEntity::class
    ],
    version = MedAppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(MedAppConverters::class)
abstract class MedAppDatabase : RoomDatabase() {

    companion object {
        const val VERSION = 1
        const val NAME = "medapp.db"
    }
}
