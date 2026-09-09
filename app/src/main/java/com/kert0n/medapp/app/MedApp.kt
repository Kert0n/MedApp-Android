package com.kert0n.medapp.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка входа графа зависимостей. Всё, что живёт дольше экрана — база, клиенты, очередь —
 * получает область приложения отсюда, а не создаётся по месту.
 */
@HiltAndroidApp
class MedApp : Application()
