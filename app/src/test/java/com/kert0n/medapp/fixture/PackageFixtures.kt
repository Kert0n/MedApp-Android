package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.model.pack.Claims
import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageAccess
import com.kert0n.medapp.domain.model.pack.PackageFacts
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.value.Money
import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

val PACK: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000021")

/**
 * Пачка: тест называет только то, что проверяет, и не тонет в двадцати аргументах.
 *
 * Описательные поля принимаются россыпью и собираются в [PackageFacts] здесь — так тесту не
 * приходится знать, из чего состоит пачка, чтобы поменять в ней одно название.
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
    claims: Claims? = null,
    lifecycle: PackageLifecycle = PackageLifecycle.ACTIVE,
    access: PackageAccess = PackageAccess.AVAILABLE
) = Package(
    id = id,
    medKitId = medKitId,
    facts = PackageFacts(
        name = name,
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
        openedOn = openedOn
    ),
    quantity = quantity,
    addedAt = Instant.EPOCH,
    templateId = templateId,
    claims = claims,
    lifecycle = lifecycle,
    access = access
)

/** Сведения, взятые у пачки: круговой тест начинается с того, что уже сохранено. */
fun factsOf(pkg: Package): PackageFacts = pkg.facts
