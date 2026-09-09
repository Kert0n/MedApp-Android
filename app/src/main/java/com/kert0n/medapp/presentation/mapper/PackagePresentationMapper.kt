package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.Package
import com.kert0n.medapp.presentation.dto.ClaimsPresentationDTO
import com.kert0n.medapp.presentation.dto.MoneyPresentationDTO
import com.kert0n.medapp.presentation.dto.PackagePresentationDTO
import com.kert0n.medapp.presentation.dto.QuantityPresentationDTO

/** Применяется до stateIn/distinctUntilChanged: после них изменения сущности уже потеряны. */
fun Package.toPresentationDTO(): PackagePresentationDTO = PackagePresentationDTO(
    id = id,
    medKitId = medKitId,
    name = name,
    quantity = QuantityPresentationDTO(
        quantity.amount.stripTrailingZeros().toPlainString(), quantity.unitId
    ),
    formId = formId,
    category = category,
    manufacturer = manufacturer,
    country = country,
    description = description,
    expiresOn = expiresOn,
    defaultIntakeAmount = defaultIntakeAmount?.let {
        QuantityPresentationDTO(it.amount.stripTrailingZeros().toPlainString(), it.unitId)
    },
    note = note,
    price = price?.let {
        MoneyPresentationDTO(it.amount.stripTrailingZeros().toPlainString(), it.currencyCode)
    },
    purchasedOn = purchasedOn,
    openedOn = openedOn,
    addedAt = addedAt,
    templateId = templateId,
    version = version,
    claims = claims?.let {
        ClaimsPresentationDTO(
            total = it.total.stripTrailingZeros().toPlainString(),
            mine = it.mine?.stripTrailingZeros()?.toPlainString(),
            version = it.version
        )
    },
    status = status,
    syncedAt = syncedAt
)
