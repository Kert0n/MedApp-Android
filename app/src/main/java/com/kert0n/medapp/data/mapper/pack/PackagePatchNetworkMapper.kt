package com.kert0n.medapp.data.mapper.pack

import com.kert0n.medapp.data.remote.dto.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.data.sync.pack.PackageSyncState
import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageFacts

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
