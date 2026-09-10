package com.kert0n.medapp.presentation

/**
 * Разобранный ввод: значение для домена — или причина, по которой ввод отвергнут.
 *
 * Тип назван тем, что он есть, а не приёмом, которым получен: «приведение» — это то, что делает
 * маппер, а здесь лежит его результат (PLAN H1).
 *
 * Не `Result`: неверный ввод — не сбой программы, а обычное состояние формы, и экрану нужен не
 * `Throwable`, а причина, по которой он подсветит поле. Сетевой маппер устроен так же — он тоже
 * возвращает свой итог с отчётом о том, что не прошло (`PackagePatchNetworkMapping`).
 *
 * Причина — значение перечисления, без текста: сообщение человеку живёт в `R.string.*`, иначе
 * маппер начал бы решать, на каком языке говорит приложение (PLAN H1).
 */
sealed interface ParsedInput<out V, out E> {

    data class Parsed<V>(val value: V) : ParsedInput<V, Nothing>

    data class Rejected<E>(val error: E) : ParsedInput<Nothing, E>

    val valueOrNull: V? get() = (this as? Parsed)?.value

    val errorOrNull: E? get() = (this as? Rejected)?.error
}
