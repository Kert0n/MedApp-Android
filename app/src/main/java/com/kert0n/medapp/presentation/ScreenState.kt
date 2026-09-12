package com.kert0n.medapp.presentation

/**
 * Что экран показывает вместо содержимого, пока содержимого нет. Случая три, и человек различает
 * их по тому, что может сделать: при [Loading] — ждать, при [Failed] — нажать «повторить», при
 * [Ready] — читать и действовать.
 *
 * «Пусто» отдельным случаем не заводится: пустой список — это [Ready] с пустым содержимым, и
 * различает их экран, а не состояние. Завести четвёртый случай значило бы спрашивать у каждого
 * владельца данных, пусто у него или нет, — а он и так отдаёт содержимое.
 */
sealed interface ScreenState<out T> {

    data object Loading : ScreenState<Nothing>

    /** Не вышло, и названа причина: текст по ней берёт экран из `R.string.*`. */
    data class Failed(val reason: LoadFailure) : ScreenState<Nothing>

    data class Ready<T>(val value: T) : ScreenState<T>
}
