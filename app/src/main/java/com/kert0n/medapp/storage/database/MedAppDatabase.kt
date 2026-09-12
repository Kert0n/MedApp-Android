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
         * Три решения разом, и все три меняют схему, а не код (PLAN F2):
         *
         * - движение стало записью о пачке: колонок аптечек нет, переносов как вида нет (D7);
         * - приём переживает удаление пачки: ключи на неё — `SET NULL` (D6);
         * - у пачки появились части, уходящие вместе с ней, — детали, брони, движения и связи с
         *   курсами: `CASCADE` (D3).
         *
         * Ни убрать колонку с внешним ключом, ни поменять его поведение SQLite не умеет, поэтому
         * каждая задетая таблица пересоздаётся и переливается.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.rebuild(
                    table = "stock_adjustments",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `stock_adjustments_new` (
                            `id` TEXT NOT NULL, `package_id` TEXT NOT NULL, `kind` TEXT NOT NULL,
                            `unit_id` TEXT NOT NULL, `observed_at` INTEGER NOT NULL,
                            `occurred_at` INTEGER, `amount` TEXT, `before_amount` TEXT,
                            `after_amount` TEXT, `delta` TEXT, `reason` TEXT, `note` TEXT,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent(),
                    // Переносы не переезжают: они говорили только о местах, а место у пачки одно
                    // и известно ей самой.
                    copy = """
                        INSERT INTO `stock_adjustments_new`
                            (`id`, `package_id`, `kind`, `unit_id`, `observed_at`, `occurred_at`,
                             `amount`, `before_amount`, `after_amount`, `delta`, `reason`, `note`)
                        SELECT `id`, `package_id`, `kind`, `unit_id`, `observed_at`, `occurred_at`,
                               `amount`, `before_amount`, `after_amount`, `delta`, `reason`, `note`
                        FROM `stock_adjustments` WHERE `kind` != 'TRANSFER'
                    """.trimIndent(),
                    "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_package_id_observed_at` " +
                        "ON `stock_adjustments` (`package_id`, `observed_at`)"
                )
                connection.rebuild(
                    table = "intakes",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `intakes_new` (
                            `id` TEXT NOT NULL, `unit_id` TEXT NOT NULL, `status` TEXT NOT NULL,
                            `course_id` TEXT, `course_revision` INTEGER, `scheduled_on` TEXT,
                            `scheduled_time` INTEGER, `scheduled_at` INTEGER,
                            `planned_amount` TEXT, `planned_package_id` TEXT, `answered_at` INTEGER,
                            `taken_package_id` TEXT, `taken_amount` TEXT,
                            `accounting` TEXT NOT NULL, `operation_id` TEXT,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`course_id`) REFERENCES `course_records`(`id`)
                                ON UPDATE NO ACTION ON DELETE RESTRICT ,
                            FOREIGN KEY(`planned_package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE SET NULL ,
                            FOREIGN KEY(`taken_package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                    """.trimIndent(),
                    copy = "INSERT INTO `intakes_new` SELECT * FROM `intakes`",
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_intakes_course_id_scheduled_on_scheduled_time` " +
                        "ON `intakes` (`course_id`, `scheduled_on`, `scheduled_time`)",
                    "CREATE INDEX IF NOT EXISTS `index_intakes_planned_package_id` " +
                        "ON `intakes` (`planned_package_id`)",
                    "CREATE INDEX IF NOT EXISTS `index_intakes_taken_package_id` " +
                        "ON `intakes` (`taken_package_id`)",
                    "CREATE INDEX IF NOT EXISTS `index_intakes_operation_id` " +
                        "ON `intakes` (`operation_id`)"
                )
                connection.rebuild(
                    table = "package_details",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `package_details_new` (
                            `package_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL,
                            `expires_on` TEXT, `default_intake_amount` TEXT,
                            `default_intake_unit_id` TEXT, `note` TEXT, `price` TEXT,
                            `currency` TEXT, `purchased_on` TEXT, `opened_on` TEXT,
                            `template_id` TEXT,
                            PRIMARY KEY(`package_id`),
                            FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent(),
                    copy = "INSERT INTO `package_details_new` SELECT * FROM `package_details`"
                )
                connection.rebuild(
                    table = "claims",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `claims_new` (
                            `package_id` TEXT NOT NULL, `total` TEXT NOT NULL, `mine` TEXT,
                            PRIMARY KEY(`package_id`),
                            FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent(),
                    copy = "INSERT INTO `claims_new` SELECT * FROM `claims`"
                )
                connection.rebuild(
                    table = "course_sources",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `course_sources_new` (
                            `course_id` TEXT NOT NULL, `package_id` TEXT NOT NULL,
                            `position` INTEGER NOT NULL, `allocated_doses` INTEGER NOT NULL,
                            PRIMARY KEY(`course_id`, `package_id`),
                            FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`)
                                ON UPDATE NO ACTION ON DELETE RESTRICT ,
                            FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent(),
                    copy = "INSERT INTO `course_sources_new` SELECT * FROM `course_sources`",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_course_sources_course_id_position` " +
                        "ON `course_sources` (`course_id`, `position`)",
                    "CREATE INDEX IF NOT EXISTS `index_course_sources_package_id` " +
                        "ON `course_sources` (`package_id`)"
                )
                connection.rebuild(
                    table = "active_package_assignments",
                    createNew = """
                        CREATE TABLE IF NOT EXISTS `active_package_assignments_new` (
                            `package_id` TEXT NOT NULL, `course_id` TEXT NOT NULL,
                            PRIMARY KEY(`package_id`),
                            FOREIGN KEY(`package_id`) REFERENCES `packages`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE ,
                            FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`)
                                ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent(),
                    copy = "INSERT INTO `active_package_assignments_new` " +
                        "SELECT * FROM `active_package_assignments`",
                    "CREATE INDEX IF NOT EXISTS `index_active_package_assignments_course_id` " +
                        "ON `active_package_assignments` (`course_id`)"
                )
            }
        }

        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}

/**
 * Пересоздание таблицы под новую схему: SQLite не умеет ни убрать колонку с внешним ключом, ни
 * поменять его поведение. Строки переливаются тем запросом, который называет вызывающий, — у
 * таблицы, потерявшей колонки, он не «звёздочка».
 */
private fun SQLiteConnection.rebuild(
    table: String,
    createNew: String,
    copy: String,
    vararg indices: String
) {
    execSQL(createNew)
    execSQL(copy)
    execSQL("DROP TABLE `$table`")
    execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
    indices.forEach(::execSQL)
}
