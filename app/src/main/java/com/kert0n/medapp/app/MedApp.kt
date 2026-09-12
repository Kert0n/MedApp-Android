package com.kert0n.medapp.app

import android.app.Application
import com.kert0n.medapp.queue.QueueOutbox
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Точка входа графа зависимостей. Всё, что живёт дольше экрана — база, клиенты, очередь —
 * получает область приложения отсюда, а не создаётся по месту. Очередь просыпается вместе с
 * процессом: в ней могли остаться операции с прошлого запуска (PLAN E4).
 */
@HiltAndroidApp
class MedApp : Application() {

    @Inject
    lateinit var outbox: QueueOutbox

    override fun onCreate() {
        super.onCreate()
        outbox.start()
    }
}
