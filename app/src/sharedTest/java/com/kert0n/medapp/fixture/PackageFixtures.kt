package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.pack.PackageAvailability
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.ExpiryDate
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Money
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

val PACK: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000021")

/** Вторая пачка того же лекарства: стек источников начинается с двух пачек, а не с одной. */
val OTHER_PACK: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000022")

/**
 * Пачка: тест называет только то, что проверяет, и не тонет в двадцати аргументах.
 *
 * Описательные поля принимаются россыпью и собираются в [PackageFacts] здесь — так тесту не
 * приходится знать, из чего состоит пачка, чтобы поменять в ней одно название.
 */
fun pack(
    id: Uuid = PACK,
    medKit: MedKit = medKit(id = HOME_KIT),
    name: String = "Парацетамол",
    quantity: Quantity = tablets("20"),
    form: DosageForm? = null,
    category: String? = null,
    manufacturer: String? = null,
    country: String? = null,
    description: String? = null,
    expiresOn: ExpiryDate? = null,
    defaultIntakeAmount: Dose? = null,
    note: String? = null,
    price: Money? = null,
    purchasedOn: LocalDate? = null,
    openedOn: LocalDate? = null,
    templateId: Uuid? = null,
    claims: Claims? = null,
    lifecycle: Package.Lifecycle = Package.Lifecycle.ACTIVE,
    access: Package.Access = Package.Access.AVAILABLE
) = Package(
    id = id,
    medKit = medKit,
    facts = PackageFacts(
        shared = PackageSharedFacts(
            name = name,
            form = form,
            category = category,
            manufacturer = manufacturer,
            country = country,
            description = description
        ),
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

/** Правка одного общего поля: композиция читается в тесте как «та же пачка, другое название». */
fun PackageFacts.withShared(
    name: String = shared.name,
    form: DosageForm? = shared.form,
    category: String? = shared.category,
    manufacturer: String? = shared.manufacturer,
    country: String? = shared.country,
    description: String? = shared.description
): PackageFacts = copy(
    shared = PackageSharedFacts(name, form, category, manufacturer, country, description)
)

/** Доступность пачки для тестов, которым нужны её числа: оценка равна остатку пачки. */
fun packAvailability(
    id: Uuid = PACK,
    quantity: Quantity = tablets("20"),
    claims: Claims? = null,
    expiresOn: ExpiryDate? = null,
    myAllocation: Quantity = Quantity.zero(quantity.unit)
): PackageAvailability = PackageAvailability(
    pkg = pack(id = id, quantity = quantity, claims = claims, expiresOn = expiresOn),
    effective = quantity,
    myAllocation = myAllocation
)

/** Срок годности из записи «2027-03-31»: в тестах читается как на упаковке. */
fun expiry(lastDay: String): ExpiryDate = ExpiryDate(LocalDate.parse(lastDay))
