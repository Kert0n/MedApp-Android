package com.kert0n.medapp.data.mapper

import com.kert0n.medapp.data.remote.dto.PackagePatchNetworkDTO
import com.kert0n.medapp.data.remote.dto.PackagePostNetworkDTO
import com.kert0n.medapp.data.sync.PackageSyncState
import com.kert0n.medapp.domain.model.Package
import com.kert0n.medapp.domain.model.PackageFacts

/**
 * Что из локальной правки уезжает на сервер и что уехать не может.
 *
 * [formIdClearUnsupported] — не мелочь интерфейса. Очистить UUID формы серверным PATCH сейчас
 * нельзя: `null` значит «не менять», а `""` не является UUID. Локально форму убрать можно, и
 * тогда экран обязан объяснить ограничение — иначе неудалённая серверная форма выдавалась бы за
 * очищенную (PLAN D3).
 */
data class PackagePatchNetworkMapping(
    val dto: PackagePatchNetworkDTO?,          // null — серверных полей не изменилось
    val formIdClearUnsupported: Boolean
)

/**
 * Серверная часть упаковки для создания: домен → провод.
 *
 * Здесь и заканчивается граница данных (PLAN C0): всё, чего нет в [PackagePostNetworkDTO], остаётся
 * на устройстве, потому что взять это на проводе просто негде.
 */
fun Package.toPostNetworkDTO(): PackagePostNetworkDTO = PackagePostNetworkDTO(
    name = facts.name,
    amount = quantity.toNetworkAmount(),
    unitId = quantity.unitId,
    formId = facts.formId,
    category = facts.category,
    manufacturer = facts.manufacturer,
    country = facts.country,
    description = facts.description
)

/**
 * Сравнивает сохранённую доменную форму с тем, что известно о пачке, и оставляет только
 * изменившееся.
 *
 * Отправлять поле, которого человек не трогал, нельзя: PATCH перетёр бы им чужую правку тем же
 * самым значением, и потеря выглядела бы как «ничего не менялось».
 *
 * Состояние синхронизации приходит аргументом, а не читается у пачки: «существует ли она на
 * сервере» — вопрос к предусловию, и домен на него не отвечает.
 *
 * Количество и единица здесь не заполняются вовсе — это отдельные сценарии пересчёта и смены
 * единицы (PLAN D3, E1), у них свои операции и свои предупреждения.
 */
fun PackageFacts.toPatchNetworkMapping(
    current: Package,
    sync: PackageSyncState
): PackagePatchNetworkMapping {
    val known = current.facts
    val dto = PackagePatchNetworkDTO(
        name = name.takeIf { it != known.name },
        formId = formId.takeIf { it != null && it != known.formId },
        category = clearableText(known.category, category),
        manufacturer = clearableText(known.manufacturer, manufacturer),
        country = clearableText(known.country, country),
        description = clearableText(known.description, description)
    )
    val formCleared = known.formId != null && formId == null
    return PackagePatchNetworkMapping(
        dto = dto.takeIf { !it.isEmpty },
        formIdClearUnsupported = formCleared && sync.isOnServer
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
