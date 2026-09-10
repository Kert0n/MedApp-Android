package com.kert0n.medapp.data.mapper.pack

import com.kert0n.medapp.data.mapper.value.toNetworkAmount
import com.kert0n.medapp.data.remote.dto.pack.PackagePostNetworkDTO
import com.kert0n.medapp.domain.model.pack.Package

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
