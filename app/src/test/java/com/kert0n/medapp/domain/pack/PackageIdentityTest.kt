package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.medkit.KitPublication
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.value.Quantity

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets

import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Упаковка и аптечка — сущности: тождество переживает изменение полей, а конструктор проверяет
 * то, что верно про них всегда (PLAN D2, D3).
 */
class PackageIdentityTest {

    @Test
    fun packWithLessLeftIsTheSamePack() {
        val full = pack(quantity = tablets("20"))
        val used = full.consume(tablets("1"))
        assertEquals(full, used)
        assertEquals(full.hashCode(), used.hashCode())
    }

    @Test
    fun differentPacksWithIdenticalContentsAreNotTheSame() {
        // Одинаковые названия не объединяют пачки: две коробки — две вещи (PLAN C0).
        val other = Uuid.parse("00000000-0000-4000-8000-0000000000ff")
        assertNotEquals(pack(id = PACK), pack(id = other))
    }

    @Test
    fun packIsBuiltByItsOwnConstructor() {
        // Фабрики нет: когда пачку позволено завести — правило сценария добавления, а не модели.
        val built = Package(
            id = PACK,
            medKitId = HOME_KIT,
            facts = PackageFacts(PackageSharedFacts(name = "Парацетамол", formId = TABLET_FORM)),
            quantity = tablets("20"),
            addedAt = Instant.EPOCH
        )
        assertEquals(PackageLifecycle.ACTIVE, built.lifecycle)
        assertEquals(PackageAccess.AVAILABLE, built.access)
        assertEquals("Парацетамол", built.name)
        assertEquals(TABLET_FORM, built.facts.formId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun activePackIsNeverEmpty() {
        // Инвариант, верный всегда: и при заведении, и при чтении сохранённого состояния.
        pack(quantity = Quantity.zero(TABLETS), lifecycle = PackageLifecycle.ACTIVE)
    }

    @Test
    fun archivedPackWithNothingLeftIsLegitimate() {
        val archived =
            pack(quantity = Quantity.zero(TABLETS), lifecycle = PackageLifecycle.ARCHIVED)
        assertTrue(archived.quantity.isZero)
        assertEquals(PackageLifecycle.ARCHIVED, archived.lifecycle)
    }

    @Test
    fun renamedKitIsTheSameKit() {
        val created = MedKit(HOME_KIT, "Домашняя", null, KitPublication.LOCAL, 1, Instant.EPOCH)
        assertEquals(created, created.describe("Дачная", "верхняя полка"))
        assertEquals(created.hashCode(), created.describe("Дачная", null).hashCode())
    }

    @Test
    fun descriptiveFactsCarryTheirOwnInvariantsOnce() {
        // Границы длин объявлены сведениями, и Package их не переобъявляет.
        val tooLong = runCatching {
            PackageFacts(PackageSharedFacts(name = "я".repeat(PACKAGE_NAME_MAX_LENGTH + 1)))
        }
        assertTrue(tooLong.isFailure)
    }
}
