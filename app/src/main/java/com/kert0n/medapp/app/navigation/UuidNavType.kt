package com.kert0n.medapp.app.navigation

import android.os.Bundle
import androidx.navigation.NavType
import kotlin.uuid.Uuid

/**
 * Как идентификатор едет в маршруте. Сериализация `Uuid` у `kotlinx.serialization` есть, но
 * навигации нужно ещё и это: чем аргумент кладётся в состояние и как читается обратно.
 *
 * Заведён один раз на всё приложение: маршрутов с идентификаторами будет много, и второй такой же
 * разъехался бы с первым.
 *
 * `null` — «аргумента нет»: у формы это значит «заводим новое», а не «непонятно что».
 */
object UuidNavType : NavType<Uuid?>(isNullableAllowed = true) {

    override fun put(bundle: Bundle, key: String, value: Uuid?) {
        bundle.putString(key, value?.toString())
    }

    override fun get(bundle: Bundle, key: String): Uuid? = bundle.getString(key)?.let(Uuid::parse)

    /** Навигация отсутствующий аргумент передаёт словом `null`, а не пустой строкой. */
    override fun parseValue(value: String): Uuid? =
        if (value == "null" || value.isEmpty()) null else Uuid.parse(value)

    override fun serializeAsValue(value: Uuid?): String = value?.toString() ?: "null"
}
