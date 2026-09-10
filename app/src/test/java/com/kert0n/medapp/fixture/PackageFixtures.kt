package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.calc.availability.PackageAvailability
import com.kert0n.medapp.domain.calc.availability.availabilityOf
import com.kert0n.medapp.domain.model.pack.Claims
import com.kert0n.medapp.domain.model.pack.EffectiveAmount
import com.kert0n.medapp.domain.model.pack.ExpiryDate
import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageAccess
import com.kert0n.medapp.domain.model.pack.PackageFacts
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.pack.PackageSharedFacts
import com.kert0n.medapp.domain.model.value.Money
import com.kert0n.medapp.domain.model.value.Quantity
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
    medKitId: Uuid = HOME_KIT,
    name: String = "Парацетамол",
    quantity: Quantity = tablets("20"),
    formId: Uuid? = null,
    category: String? = null,
    manufacturer: String? = null,
    country: String? = null,
    description: String? = null,
    expiresOn: ExpiryDate? = null,
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
        shared = PackageSharedFacts(
            name = name,
            formId = formId,
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
    formId: Uuid? = shared.formId,
    category: String? = shared.category,
    manufacturer: String? = shared.manufacturer,
    country: String? = shared.country,
    description: String? = shared.description
): PackageFacts = copy(
    shared = PackageSharedFacts(name, formId, category, manufacturer, country, description)
)

/**
 * Доступность пачки для тестов, которым нужна не сама проекция, а её числа.
 *
 * Оценка количества собирается здесь напрямую, а не свёрткой очереди: свёртка живёт в слое данных
 * и к фикстурам домена отношения не имеет.
 */
fun packAvailability(
    id: Uuid = PACK,
    quantity: Quantity = tablets("20"),
    claims: Claims? = null,
    expiresOn: ExpiryDate? = null,
    unresolvedOperationIds: List<Uuid> = emptyList(),
    confirmed: Boolean = true,
    myAllocation: Quantity = Quantity.zero(quantity.unitId)
): PackageAvailability = availabilityOf(
    pkg = pack(id = id, quantity = quantity, claims = claims, expiresOn = expiresOn),
    amount = if (unresolvedOperationIds.isEmpty()) EffectiveAmount.Known(quantity, confirmed)
    else EffectiveAmount.NeedsRecount(quantity, unresolvedOperationIds),
    myAllocation = myAllocation
)

/** Срок годности из записи «2027-03-31»: в тестах читается как на упаковке. */
fun expiry(lastDay: String): ExpiryDate = ExpiryDate(LocalDate.parse(lastDay))
