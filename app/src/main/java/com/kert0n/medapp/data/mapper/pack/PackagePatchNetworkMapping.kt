package com.kert0n.medapp.data.mapper.pack

import com.kert0n.medapp.data.remote.dto.pack.PackagePatchNetworkDTO

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
