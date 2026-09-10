package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Money
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Круговое преобразование упаковки: серверная часть и личные сведения хранятся порознь, а
 * собранная обратно пачка равна исходной по каждому полю (PLAN F1).
 */
class PackageStorageMapperTest {

    private val full: Package = pack(
        name = "Парацетамол",
        quantity = tablets("19.5"),
        formId = TABLET_FORM,
        category = "Обезболивающие",
        manufacturer = "Завод",
        country = "Россия",
        description = "Таблетки, покрытые оболочкой",
        expiresOn = expiry("2027-03-31"),
        defaultIntakeAmount = dose("0.5"),
        note = "в верхнем ящике",
        price = Money(BigDecimal("199.90")),
        purchasedOn = LocalDate.of(2026, 1, 15),
        openedOn = LocalDate.of(2026, 2, 1),
        templateId = TABLETS
    )

    private fun rowOf(pkg: Package, sync: PackageSyncState = PackageSyncState(pkg.id)) =
        PackageStorageRow(pkg.toStorageEntity(sync), pkg.toDetailsStorageEntity())

    @Test
    fun everyFactSurvivesTheRoundTrip() {
        val restored = rowOf(full).toDomain()
        assertEquals(full.id, restored.id)
        assertEquals(full.medKitId, restored.medKitId)
        assertEquals(full.quantity, restored.quantity)
        assertEquals(full.addedAt, restored.addedAt)
        assertEquals(full.templateId, restored.templateId)
        assertEquals(full.lifecycle, restored.lifecycle)
        assertEquals(full.access, restored.access)
        assertEquals(full.facts, restored.facts)
    }

    @Test
    fun absentFactsStayAbsent() {
        val bare = pack(quantity = tablets("1"))
        val restored = rowOf(bare).toDomain()
        assertEquals(bare.facts, restored.facts)
        assertNull(restored.facts.expiresOn)
        assertNull(restored.facts.price)
        assertNull(restored.facts.defaultIntakeAmount)
        assertNull(restored.templateId)
    }

    @Test
    fun archivedEmptyPackageIsRestorable() {
        val archived = pack(quantity = tablets("0"), lifecycle = Package.Lifecycle.ARCHIVED)
        assertEquals(archived.quantity, rowOf(archived).toDomain().quantity)
        assertEquals(Package.Lifecycle.ARCHIVED, rowOf(archived).toDomain().lifecycle)
    }

    /** Обвязка доставки едет в колонках, а не в пачке: домен её обратно не получает. */
    @Test
    fun syncStateTravelsInColumnsAndNotInTheDomainPackage() {
        val sync = PackageSyncState(
            packageId = PACK,
            version = ResourceVersion(7),
            claimsVersion = ResourceVersion(3),
            syncedAt = Instant.parse("2026-09-10T12:00:00Z")
        )
        val stored = full.toStorageEntity(sync)
        assertEquals(sync, stored.syncState())
        assertEquals(full.facts, PackageStorageRow(stored, full.toDetailsStorageEntity()).toDomain().facts)
    }

    @Test
    fun sharedFactsAreTheServerPartAndNothingElse() {
        assertEquals(full.facts.shared, full.toStorageEntity().sharedFacts())
    }

    @Test
    fun syncStateOfAnotherPackageIsRejected() {
        val alien = PackageSyncState(packageId = HOME_KIT, version = ResourceVersion(1))
        val failure = runCatching { full.toStorageEntity(alien) }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class, failure!!::class)
    }

    /** Момент первого наблюдения — единственное, что известно о чужой пачке из снимка. */
    @Test
    fun observedDetailsCarryOnlyTheMomentOfFirstSighting() {
        val at = Instant.parse("2026-09-10T12:00:00Z")
        val details = observedPackageDetails(PACK, at)
        assertEquals(at, details.addedAt)
        assertNull(details.expiresOn)
        assertNull(details.note)
        assertNull(details.price)
    }
}
