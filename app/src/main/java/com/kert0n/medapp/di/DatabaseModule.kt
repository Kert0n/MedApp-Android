package com.kert0n.medapp.di

import android.content.Context
import androidx.room.Room
import com.kert0n.medapp.storage.database.MedAppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * База поднимается графом в одном экземпляре: два экземпляра над одним файлом не видели бы
 * транзакций друг друга.
 *
 * Разрушающий откат не включается ни в каком виде: он молча стирает очередь и историю при
 * обновлении приложения, и вместо потери данных нужен упавший переход (PLAN F4).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun medAppDatabase(@ApplicationContext context: Context): MedAppDatabase =
        Room.databaseBuilder(context, MedAppDatabase::class.java, MedAppDatabase.NAME).build()
}
