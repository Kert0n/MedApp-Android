package com.kert0n.medapp.network.account

/**
 * Что устройство знает о своей учётке. Случаев три, потому что поведение у них разное: нет
 * учётки — регистрироваться; есть — работать; **нечитаема** — спросить человека, а не заводить
 * молча новую поверх локальных данных: старый ключ выдан один раз, и брони на нём уже не снять
 * (PLAN G2).
 */
sealed interface StoredAccount {

    data object Absent : StoredAccount

    data class Present(val credentials: AccountCredentials) : StoredAccount

    data object Unreadable : StoredAccount
}
