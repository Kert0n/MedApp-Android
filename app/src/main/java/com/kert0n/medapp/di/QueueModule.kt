package com.kert0n.medapp.di

import com.kert0n.medapp.queue.QueueSending
import com.kert0n.medapp.queue.QueueWorkerSending
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Очередь выдаётся графом своими портами: кто ставит команды, знает только, что серверу пора
 * узнать, а чем и когда это отправляется — дело работника (PLAN E2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QueueModule {

    @Binds
    @Singleton
    abstract fun sending(implementation: QueueWorkerSending): QueueSending
}
