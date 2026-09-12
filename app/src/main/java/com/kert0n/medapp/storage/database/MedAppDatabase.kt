package com.kert0n.medapp.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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
import com.kert0n.medapp.storage.server.NotificationLogDao
import com.kert0n.medapp.storage.server.NotificationLogStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.server.SyncOperationDependencyStorageEntity
import com.kert0n.medapp.storage.server.SyncOperationStorageEntity
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.stock.StockMovementStorageEntity
import com.kert0n.medapp.storage.value.DosageFormStorageEntity
import com.kert0n.medapp.storage.value.QuantityUnitStorageEntity
import com.kert0n.medapp.storage.value.VocabularyDao

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
        SyncOperationDependencyStorageEntity::class,
        NotificationLogStorageEntity::class
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

    abstract fun notificationLog(): NotificationLogDao

    abstract fun vocabulary(): VocabularyDao

    companion object {
        const val VERSION = 3
        const val NAME = "medapp.db"

        /** Факт «замороженный запрос уходил, исход неизвестен» получил свою колонку (PLAN E3). */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE sync_operations ADD COLUMN outcome_unknown INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Движение стало записью о пачке: аптечек в нём нет и переносов как вида нет (PLAN D7).
         * Приём стал переживать удаление пачки: ключи на неё — `SET NULL` (PLAN D6). Ни убрать
         * колонку с внешним ключом, ни поменять его поведение SQLite не умеет, поэтому обе
         * таблицы пересоздаются и переливаются.
         *
         * Строки переносов не переносятся: они говорили только о местах, а место у пачки одно и
         * известно ей самой.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_adjustments_new` (
                        `id` TEXT NOT NULL, `package_id` TEXT NOT NULL, `kind` TEXT NOT NULL,
                        `unit_id` TEXT NOT NULL, `observed_at` INTEGER NOT NULL,
                        `occurred_at` INTEGER, `amount` TEXT, `before_amount` TEXT,
                        `after_amount` TEXT, `delta` TEXT, `reason` TEXT, `note` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO `stock_adjustments_new`
                        (`id`, `package_id`, `kind`, `unit_id`, `observed_at`, `occurred_at`,
                         `amount`, `before_amount`, `after_amount`, `delta`, `reason`, `note`)
                    SELECT `id`, `package_id`, `kind`, `unit_id`, `observed_at`, `occurred_at`,
                           `amount`, `before_amount`, `after_amount`, `delta`, `reason`, `note`
                    FROM `stock_adjustments` WHERE `kind` != 'TRANSFER'
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE `stock_adjustments`")
                connection.execSQL("ALTER TABLE `stock_adjustments_new` RENAME TO `stock_adjustments`")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_package_id_observed_at` " +
                        "ON `stock_adjustments` (`package_id`, `observed_at`)"
                )

                // Приём переживает удаление пачки: ссылка пустеет, факт остаётся (PLAN D6).
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intakes_new` (
                        `id` TEXT NOT NULL, `unit_id` TEXT NOT NULL, `status` TEXT NOT NULL,
                        `course_id` TEXT, `course_revision` INTEGER, `scheduled_on` TEXT,
                        `scheduled_time` INTEGER, `scheduled_at` INTEGER, `planned_amount` TEXT,
                        `planned_package_id` TEXT, `answered_at` INTEGER, `taken_package_id` TEXT,
                        `taken_amount` TEXT, `accounting` TEXT NOT NULL, `operation_id` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`course_id`) REFERENCES `course_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT ,
                        FOREIGN KEY(`planned_package_id`) REFERENCES `packages`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL ,
                        FOREIGN KEY(`taken_package_id`) REFERENCES `packages`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL("INSERT INTO `intakes_new` SELECT * FROM `intakes`")
                connection.execSQL("DROP TABLE `intakes`")
                connection.execSQL("ALTER TABLE `intakes_new` RENAME TO `intakes`")
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_intakes_course_id_scheduled_on_scheduled_time` " +
                        "ON `intakes` (`course_id`, `scheduled_on`, `scheduled_time`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intakes_planned_package_id` " +
                        "ON `intakes` (`planned_package_id`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intakes_taken_package_id` " +
                        "ON `intakes` (`taken_package_id`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intakes_operation_id` " +
                        "ON `intakes` (`operation_id`)"
                )
            }
        }

        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
