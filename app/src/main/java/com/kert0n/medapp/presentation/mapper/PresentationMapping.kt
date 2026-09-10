package com.kert0n.medapp.presentation.mapper

/**
 * Итог приведения DTO представления к домену.
 *
 * Не `Result`: неверный ввод — не сбой программы, а обычное состояние формы, и экрану нужен не
 * `Throwable`, а причина, по которой он подсветит поле. Сетевой маппер устроен так же — он тоже
 * возвращает свой итог с отчётом о том, что не прошло (`PackagePatchNetworkMapping`).
 *
 * Причина — значение перечисления, без текста: сообщение человеку живёт в `R.string.*`, иначе
 * маппер начал бы решать, на каком языке говорит приложение (PLAN H1).
 */
sealed interface PresentationMapping<out V, out E> {

    data class Mapped<V>(val value: V) : PresentationMapping<V, Nothing>

    data class Rejected<E>(val error: E) : PresentationMapping<Nothing, E>

    val valueOrNull: V? get() = (this as? Mapped)?.value

    val errorOrNull: E? get() = (this as? Rejected)?.error
}
