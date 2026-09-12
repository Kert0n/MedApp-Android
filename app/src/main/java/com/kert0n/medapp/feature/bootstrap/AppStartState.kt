package com.kert0n.medapp.feature.bootstrap

import com.kert0n.medapp.domain.Unavailability

/**
 * С чего начинается приложение. Случая четыре, и каждый ведёт человека к своему действию:
 * при [Checking] нажимать нечего, при [Setup] — «повторить», при [KeyLost] нужно его решение,
 * при [Ready] — приложение.
 *
 * [KeyLost] отделён от [Setup] намеренно: сохранённое есть, но не открывается, и молча завести
 * вторую учётку поверх локальных данных нельзя — приложение показывает состояние и спрашивает
 * (PLAN G2). Склеенные, эти два случая дали бы «повторить», который перерегистрирует.
 */
sealed interface AppStartState {

    data object Checking : AppStartState

    /** Настройка не прошла, и названо почему; повтор осмыслен по правилам самой причины. */
    data class Setup(val reason: Unavailability) : AppStartState

    data object KeyLost : AppStartState

    data object Ready : AppStartState
}
