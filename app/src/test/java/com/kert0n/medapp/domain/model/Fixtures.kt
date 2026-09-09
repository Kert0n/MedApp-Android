package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Постоянные идентификаторы вместо случайных: упавший тест должен читаться по сообщению,
 * а не воспроизводиться заново со своим набором UUID.
 */
val TABLETS: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
val MILLILITRES: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000002")

fun tablets(amount: String): Quantity = Quantity(BigDecimal(amount), TABLETS)

fun millilitres(amount: String): Quantity = Quantity(BigDecimal(amount), MILLILITRES)

val HOME_KIT: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000011")
val SHARED_KIT: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000012")

val PACK: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000021")

/**
 * Пачка со всеми обязательными полями и без единого необязательного: тест называет только то,
 * что проверяет, и не тонет в двадцати аргументах.
 */
fun pack(
    id: Uuid = PACK,
    medKitId: Uuid = HOME_KIT,
    name: String = "Парацетамол",
    quantity: Quantity = tablets("20"),
    formId: Uuid? = null,
    category: String? = null,
    manufacturer: String? = null,
    country: String? = null,
    description: String? = null,
    expiresOn: LocalDate? = null,
    defaultIntakeAmount: Quantity? = null,
    note: String? = null,
    price: Money? = null,
    purchasedOn: LocalDate? = null,
    openedOn: LocalDate? = null,
    templateId: Uuid? = null,
    version: Long? = null,
    claims: Claims? = null,
    status: PackageStatus = PackageStatus.ACTIVE,
    syncedAt: Instant? = null
) = Package(
    id = id,
    medKitId = medKitId,
    name = name,
    quantity = quantity,
    formId = formId,
    category = category,
    manufacturer = manufacturer,
    country = country,
    description = description,
    expiresOn = expiresOn,
    defaultIntakeAmount = defaultIntakeAmount,
    note = note,
    price = price,
    purchasedOn = purchasedOn,
    openedOn = openedOn,
    addedAt = Instant.EPOCH,
    templateId = templateId,
    version = version,
    claims = claims,
    status = status,
    syncedAt = syncedAt
)

val TABLET_FORM: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000041")
val CAPSULE_FORM: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000042")

/** Форма, загруженная из пачки: круговой тест начинается с того, что уже сохранено. */
fun editOf(pkg: Package) = PackageEdit(
    name = pkg.name,
    formId = pkg.formId,
    category = pkg.category,
    manufacturer = pkg.manufacturer,
    country = pkg.country,
    description = pkg.description,
    expiresOn = pkg.expiresOn,
    defaultIntakeAmount = pkg.defaultIntakeAmount,
    note = pkg.note,
    price = pkg.price,
    purchasedOn = pkg.purchasedOn,
    openedOn = pkg.openedOn
)
