package com.kert0n.medapp.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kert0n.medapp.storage.medkit.MedKitDao
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.PackageDetailsStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageEntity
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
        DosageFormStorageEntity::class,
        MedKitStorageEntity::class,
        PackageStorageEntity::class,
        PackageDetailsStorageEntity::class
    ],
    version = MedAppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(MedAppConverters::class)
abstract class MedAppDatabase : RoomDatabase() {

    abstract fun medKits(): MedKitDao

    abstract fun packages(): PackageDao

    companion object {
        const val VERSION = 1
        const val NAME = "medapp.db"
    }
}
