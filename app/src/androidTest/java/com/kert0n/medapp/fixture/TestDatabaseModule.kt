package com.kert0n.medapp.fixture

import android.content.Context
import androidx.room.Room
import com.kert0n.medapp.di.DatabaseModule
import com.kert0n.medapp.storage.course.CourseDao
import com.kert0n.medapp.storage.database.BundledVocabulary
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.IntakeDao
import com.kert0n.medapp.storage.medkit.MedKitDao
import com.kert0n.medapp.storage.pack.PackageDao
import com.kert0n.medapp.storage.server.NotificationLogDao
import com.kert0n.medapp.storage.server.SyncOperationDao
import com.kert0n.medapp.storage.stock.StockMovementDao
import com.kert0n.medapp.storage.value.VocabularyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * База проверок живёт в памяти и заводится заново на каждый тест: прогон не зависит от того, что
 * оставил предыдущий, и не оставляет ничего после себя.
 *
 * Раньше экранные проверки шли по настоящему файлу устройства, и «список аптечек пуст» было
 * правдой ровно до первой проверки, которая заводит аптечку. Порядок тестов такой зависимости не
 * лечит — её лечит своя база.
 *
 * Встроенный снимок словарей ставится тем же обратным вызовом, что и в приложении: без единиц
 * форма упаковки не заполняется, и проверять на пустом словаре было бы нечего.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun medAppDatabase(@ApplicationContext context: Context): MedAppDatabase =
        Room.inMemoryDatabaseBuilder(context, MedAppDatabase::class.java)
            .addCallback(BundledVocabulary.fromAssets(context.assets))
            .build()

    @Provides
    fun vocabularyDao(database: MedAppDatabase): VocabularyDao = database.vocabulary()

    @Provides
    fun medKitDao(database: MedAppDatabase): MedKitDao = database.medKits()

    @Provides
    fun packageDao(database: MedAppDatabase): PackageDao = database.packages()

    @Provides
    fun courseDao(database: MedAppDatabase): CourseDao = database.courses()

    @Provides
    fun intakeDao(database: MedAppDatabase): IntakeDao = database.intakes()

    @Provides
    fun stockMovementDao(database: MedAppDatabase): StockMovementDao = database.stockMovements()

    @Provides
    fun syncOperationDao(database: MedAppDatabase): SyncOperationDao = database.syncOperations()

    @Provides
    fun notificationLogDao(database: MedAppDatabase): NotificationLogDao = database.notificationLog()
}
