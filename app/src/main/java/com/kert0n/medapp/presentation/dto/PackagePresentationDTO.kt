package com.kert0n.medapp.presentation.dto

import com.kert0n.medapp.domain.model.PackageAccess
import com.kert0n.medapp.domain.model.PackageLifecycle
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Сохранённое состояние упаковки для представления, с равенством по содержимому.
 *
 * Сущность Package сравнивается по id, поэтому её нельзя вкладывать в состояние StateFlow:
 * расход и правка описания окажутся равными старому состоянию. Здесь сущности нет даже внутри
 * вложенных полей. Перечисления — общий доменный словарь, а не изменяемая сущность.
 * quantity — подтверждённый остаток; проекция ожидающих операций добавляется отдельно (PLAN D4).
 *
 * Версии предусловия здесь нет: человеку она ничего не говорит, а экрану состояния синхронизации
 * нужен момент последней сверки, который маппер получает аргументом.
 */
data class PackagePresentationDTO(
    val id: Uuid,
    val medKitId: Uuid,
    val name: String,
    val quantity: QuantityPresentationDTO,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?,
    val expiresOn: LocalDate?,
    val defaultIntakeAmount: QuantityPresentationDTO?,
    val note: String?,
    val price: MoneyPresentationDTO?,
    val purchasedOn: LocalDate?,
    val openedOn: LocalDate?,
    val addedAt: Instant,
    val templateId: Uuid?,
    val claims: ClaimsPresentationDTO?,
    val lifecycle: PackageLifecycle,
    val access: PackageAccess,
    val syncedAt: Instant?
)

/** Строки чисел нормализованы маппером: 1 и 1.000000 дают одинаковое состояние. */
data class ClaimsPresentationDTO(val total: String, val mine: String?)
