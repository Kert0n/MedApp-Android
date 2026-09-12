package com.kert0n.medapp.app.navigation

import kotlin.uuid.Uuid
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
 * Пять мест — нижняя навигация; экраны вглубь носят идентификаторы того, что показывают.
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

    /** Заведение и правка аптечки; `null` — новая, её идентификатор придумает сценарий. */
    @Serializable
    data class MedKitForm(val medKitId: Uuid? = null) : Route

    /** Что лежит в этой аптечке (экран 4). */
    @Serializable
    data class MedKitContents(val medKitId: Uuid) : Route

    /**
     * Заведение (экран 7) и правка (экран 8) упаковки: поля те же, и маршрут один. [medKitId] —
     * аптечка, из которой человек пришёл, `null` — он ещё не выбрал её; [packageId] назван, когда
     * правится уже заведённая пачка.
     */
    @Serializable
    data class PackageForm(val medKitId: Uuid? = null, val packageId: Uuid? = null) : Route

    /** Всё известное об одной упаковке (экран 6). */
    @Serializable
    data class PackageCard(val packageId: Uuid) : Route
}
