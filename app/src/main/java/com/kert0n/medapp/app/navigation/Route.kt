package com.kert0n.medapp.app.navigation

import kotlinx.serialization.Serializable

/**
 * Куда человек может попасть. Маршрут — величина: равенство по содержимому и есть «то же самое
 * место», и по нему навигация решает, возвращаться назад или вставать поверх.
 *
 * **В маршруте едут только идентификаторы, дата и режим** — ни объектов, ни ключей приглашения
 * (PLAN H3, G3). Маршрут переживает смерть процесса: он сериализуется в аргументы назначения, и
 * объект, положенный в него, к моменту восстановления был бы устаревшей копией того, что лежит в
 * базе. Ключ приглашения там же оказался бы в логах навигации.
 *
 * Пять мест — нижняя навигация; экраны вглубь добавляются своими PR и носят идентификаторы.
 */
sealed interface Route {

    @Serializable
    data object MedKits : Route

    @Serializable
    data object Plan : Route

    @Serializable
    data object Scanner : Route

    @Serializable
    data object Analytics : Route

    @Serializable
    data object Settings : Route
}
