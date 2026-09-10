package com.kert0n.medapp.network.server

import kotlin.time.Duration

/**
 * Почему операция не дала объявленного успеха — ровно в тех случаях, которые вызывающий
 * обрабатывает по-разному (PLAN B5). Код ответа и есть код ошибки: серверный `detail` сюда не
 * попадает, текст для человека живёт в ресурсах.
 */
sealed interface ApiFailure {

    /** 400: поля или бизнес-условие не приняты. Ввод сохраняют и автоматически не повторяют. */
    data class Invalid(val errors: List<FieldError>) : ApiFailure

    /** Поле запроса и что с ним не так — словами сервера, для журнала и подсветки поля. */
    data class FieldError(val field: String, val reason: String)

    /** 401 и после перевыпуска пропуска: учётку сервер не принял. */
    data object Unauthorized : ApiFailure

    /**
     * 403 регистрации: регистрационный токен сборки неверен. Это дефект конфигурации, и новую
     * учётку поверх старой из-за него не заводят.
     */
    data object RegistrationRefused : ApiFailure

    /** 404: недоступное неотличимо от несуществующего — перечитать доступ, причину не приписывать. */
    data object NotFound : ApiFailure

    /**
     * 409: идентификатор занят, бронь уже есть, участник уже вступил или версия `sync` не текущая.
     * Что это значит, решают операция и история её попыток (PLAN E3).
     */
    data object Conflict : ApiFailure

    /** 412: названная версия не текущая. Предусловие расхода автоматически не заменяют (PLAN E3). */
    data object PreconditionFailed : ApiFailure

    /** 428: версия не названа вовсе — дефект интеграции, а не состояние сервера. */
    data object PreconditionRequired : ApiFailure

    /** 429: подождать [retryAfter]; без него — ограниченный backoff вызывающего. */
    data class TooManyRequests(val retryAfter: Duration?) : ApiFailure

    /**
     * Запрос не дошёл до исполнения или чтение не удалось и после повторов: связи нет, сервер
     * недоступен, пропуска не получить. Ничего не применено — повторить позже безопасно.
     */
    data object Unavailable : ApiFailure

    /**
     * Исход изменяющей команды неизвестен: обрыв, 5xx или неразборчивый успешный ответ. Команда
     * могла примениться, поэтому вслепую её не повторяют — сначала проверка (PLAN E3).
     */
    data object OutcomeUnknown : ApiFailure

    /** Ответ вне контракта: статус, которого операция не объявляла, или тело не той формы. */
    data class Protocol(val reason: String) : ApiFailure
}
