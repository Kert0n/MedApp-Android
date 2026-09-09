package com.kert0n.medapp.data.mapper

import com.kert0n.medapp.data.remote.dto.PackageWireEdit
import com.kert0n.medapp.data.remote.dto.PackageWireFields
import com.kert0n.medapp.domain.model.Package
import com.kert0n.medapp.domain.model.PackageEdit

/**
 * Что из локальной правки уезжает на сервер и что уехать не может.
 *
 * [formIdClearUnsupported] — не мелочь интерфейса. Очистить UUID формы серверным PATCH сейчас
 * нельзя: `null` значит «не менять», а `""` не является UUID. Локально форму убрать можно, и
 * тогда экран обязан объяснить ограничение — иначе неудалённая серверная форма выдавалась бы за
 * очищенную (PLAN D3).
 */
data class PackageWirePatch(
    val edit: PackageWireEdit?,          // null — серверных полей не изменилось
    val formIdClearUnsupported: Boolean
)

/**
 * Серверная часть упаковки для создания: домен → провод.
 *
 * Здесь и заканчивается граница данных (PLAN C0): всё, чего нет в [PackageWireFields], остаётся
 * на устройстве, потому что взять это на проводе просто негде.
 */
fun Package.toWireFields(): PackageWireFields = PackageWireFields(
    name = name,
    quantity = quantity,
    formId = formId,
    category = category,
    manufacturer = manufacturer,
    country = country,
    description = description
)

/**
 * Сравнивает сохранённую доменную форму с тем, что известно о пачке, и оставляет только
 * изменившееся.
 *
 * Отправлять поле, которого человек не трогал, нельзя: PATCH перетёр бы им чужую правку тем же
 * самым значением, и потеря выглядела бы как «ничего не менялось».
 *
 * Количество и единица здесь не заполняются вовсе — это отдельные сценарии пересчёта и смены
 * единицы (PLAN D3, E1), у них свои операции и свои предупреждения.
 */
fun PackageEdit.toWirePatch(current: Package): PackageWirePatch {
    val edit = PackageWireEdit(
        name = name.takeIf { it != current.name },
        formId = formId.takeIf { it != null && it != current.formId },
        category = clearableText(current.category, category),
        manufacturer = clearableText(current.manufacturer, manufacturer),
        country = clearableText(current.country, country),
        description = clearableText(current.description, description)
    )
    val formCleared = current.formId != null && formId == null
    return PackageWirePatch(
        edit = edit.takeIf { !it.isEmpty },
        formIdClearUnsupported = formCleared && current.version != null
    )
}

/**
 * Перевод доменного «сведений нет» в сетевое «очистить».
 *
 * В домене отсутствие — `null`; на проводе `null` значит «не трогать», а очистка — `""`.
 * Один этот `when` и есть весь перевод между двумя смыслами, и он живёт в мапперe, а не в модели.
 */
private fun clearableText(current: String?, saved: String?): String? = when {
    saved == current -> null   // не изменилось — не трогаем
    saved == null -> ""        // очищено — на проводе это пустая строка
    else -> saved
}
