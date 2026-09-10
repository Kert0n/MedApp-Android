package com.kert0n.medapp.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kert0n.medapp.storage.course.ActivePackageAssignmentStorageEntity
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.course.CourseRecordStorageEntity
import com.kert0n.medapp.storage.course.CourseSourceStorageEntity
import com.kert0n.medapp.storage.course.CourseStorageEntity
import com.kert0n.medapp.storage.course.CourseTimeStorageEntity
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.intake.IntakeStorageEntity
import com.kert0n.medapp.storage.medkit.MedKitDao
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.pack.ClaimsStorageEntity
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.pack.PackageDetailsStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.server.SyncOperationDependencyStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationStorageEntity
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.StockMovementStorageEntity
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
        PackageDetailsStorageEntity::class,
        ClaimsStorageEntity::class,
        CourseStorageEntity::class,
        CourseRecordStorageEntity::class,
        CourseTimeStorageEntity::class,
        CourseSourceStorageEntity::class,
        ActivePackageAssignmentStorageEntity::class,
        IntakeStorageEntity::class,
        StockMovementStorageEntity::class,
        SyncOperationStorageEntity::class,
        SyncOperationDependencyStorageEntity::class
    ],
    version = MedAppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(MedAppConverters::class)
abstract class MedAppDatabase : RoomDatabase() {

    abstract fun medKits(): MedKitDao

    abstract fun packages(): PackageDao

    abstract fun courses(): CourseDao

    abstract fun intakes(): IntakeDao

    abstract fun stockMovements(): StockMovementDao

    abstract fun syncOperations(): SyncOperationDao

    companion object {
        const val VERSION = 1
        const val NAME = "medapp.db"
    }
}
