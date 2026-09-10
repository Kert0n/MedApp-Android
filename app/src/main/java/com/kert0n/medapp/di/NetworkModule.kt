package com.kert0n.medapp.di

import com.kert0n.medapp.BuildConfig
import com.kert0n.medapp.network.server.crptHttpClient
import com.kert0n.medapp.network.server.medAppHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MedAppHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CrptHttp

/**
 * Два клиента, а не один с настройками по месту вызова: строгость разбора и авторизация — свойство
 * сервера, к которому идёт запрос, и спутать их одной настройкой нельзя (PLAN H2, G3).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @MedAppHttp
    fun medAppHttp(): HttpClient = medAppHttpClient(OkHttp.create(), BuildConfig.BASE_URL)

    @Provides
    @Singleton
    @CrptHttp
    fun crptHttp(): HttpClient = crptHttpClient(OkHttp.create(), BuildConfig.CRPT_BASE_URL)
}
