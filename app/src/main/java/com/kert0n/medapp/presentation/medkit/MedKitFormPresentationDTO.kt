package com.kert0n.medapp.presentation.medkit

/**
 * Аптечка в том виде, в каком её держит форма: строками, как человек напечатал. Пока он печатает,
 * там бывает пусто и бывают пробелы — ни то ни другое сведением не является, и аптечкой такое
 * состояние не назовёшь (PLAN H1).
 *
 * Место хранения необязательно: пустое поле значит «не указано», а не «пустая строка» — отсутствие
 * в домене выражается только `null`.
 */
data class MedKitFormPresentationDTO(val name: String = "", val location: String = "")

/** Почему форма аптечки не годится. Текст по причине берёт экран из `R.string.*`. */
enum class MedKitFormError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    LOCATION_TOO_LONG
}
