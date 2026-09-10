package com.kert0n.medapp.domain.model.value

/**
 * Одно правило на все необязательные текстовые поля домена: поле либо отсутствует (`null`),
 * либо содержит непустой текст в пределах названной длины.
 *
 * Пустая строка сведением не является. Локальная форма выражает отсутствие через `null`
 * (PLAN D3), а на проводе очистка текста передаётся как `""` — если бы `""` жила ещё и внутри
 * домена, два способа сказать «данных нет» неизбежно разошлись бы, и очистка поля перестала бы
 * быть отличимой от значения.
 */
internal fun requireText(value: String, maxLength: Int, field: String) {
    require(value.isNotBlank()) { "$field: пустое значение не является сведением" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}

internal fun requireOptionalText(value: String?, maxLength: Int, field: String) {
    if (value != null) requireText(value, maxLength, field)
}
