package com.kert0n.medapp.presentation.mapper

import com.kert0n.medapp.domain.model.Package
import com.kert0n.medapp.presentation.dto.ClaimsPresentationDTO
import com.kert0n.medapp.presentation.dto.MoneyPresentationDTO
import com.kert0n.medapp.presentation.dto.PackagePresentationDTO
import com.kert0n.medapp.presentation.dto.QuantityPresentationDTO
import java.time.Instant

/**
 * Применяется до stateIn/distinctUntilChanged: после них изменения сущности уже потеряны.
 *
 * [syncedAt] приходит аргументом, а не из пачки: момент последней сверки принадлежит обвязке
 * синхронизации слоя данных, и домен его не хранит.
 */
fun Package.toPresentationDTO(syncedAt: Instant? = null): PackagePresentationDTO =
    PackagePresentationDTO(
        id = id,
        medKitId = medKitId,
        name = facts.name,
        quantity = quantity.toPresentationDTO(),
        formId = facts.formId,
        category = facts.category,
        manufacturer = facts.manufacturer,
        country = facts.country,
        description = facts.description,
        expiresOn = facts.expiresOn,
        defaultIntakeAmount = facts.defaultIntakeAmount?.toPresentationDTO(),
        note = facts.note,
        price = facts.price?.let {
            MoneyPresentationDTO(it.amount.stripTrailingZeros().toPlainString(), it.currencyCode)
        },
        purchasedOn = facts.purchasedOn,
        openedOn = facts.openedOn,
        addedAt = addedAt,
        templateId = templateId,
        claims = claims?.let {
            ClaimsPresentationDTO(
                total = it.total.stripTrailingZeros().toPlainString(),
                mine = it.mine?.stripTrailingZeros()?.toPlainString()
            )
        },
        lifecycle = lifecycle,
        access = access,
        syncedAt = syncedAt
    )

/** Строки нормализованы: 1 и 1.000000 дают одинаковое состояние экрана. */
private fun com.kert0n.medapp.domain.model.Quantity.toPresentationDTO() =
    QuantityPresentationDTO(amount.stripTrailingZeros().toPlainString(), unitId)
