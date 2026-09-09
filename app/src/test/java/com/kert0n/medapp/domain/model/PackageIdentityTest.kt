package com.kert0n.medapp.domain.model

import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Упаковка и аптечка — сущности: тождество переживает изменение полей, а собрать их можно только
 * названным путём. Здесь проверяется именно это, а не отдельные переходы (PLAN D2, D3).
 */
class PackageIdentityTest {

    private val facts = PackageFacts(
        name = "Парацетамол",
        formId = TABLET_FORM,
        category = null,
        manufacturer = null,
        country = null,
        description = null,
        expiresOn = null,
        defaultIntakeAmount = null,
        note = null,
        price = null,
        purchasedOn = null,
        openedOn = null
    )

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
    fun newPackIsActiveAndKnowsNothingAboutTheServer() {
        val created = Package.create(
            id = PACK,
            medKitId = HOME_KIT,
            quantity = tablets("20"),
            facts = facts,
            addedAt = Instant.EPOCH
        )
        assertEquals(PackageStatus.ACTIVE, created.status)
        assertNull(created.version)
        assertNull(created.claims)
        assertNull(created.syncedAt)
        assertEquals("Парацетамол", created.name)
        assertEquals(TABLET_FORM, created.formId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPackCannotBeCreated() {
        // Заводить нечего, и начальный остаток на проводе строго положителен (PLAN B2).
        Package.create(
            id = PACK,
            medKitId = HOME_KIT,
            quantity = Quantity.zero(TABLETS),
            facts = facts,
            addedAt = Instant.EPOCH
        )
    }

    @Test
    fun archivedPackWithNothingLeftCanBeRestored() {
        // Восстановление не решает, а возвращает решённое: такая пачка в базе законна.
        val restored = pack(quantity = Quantity.zero(TABLETS), status = PackageStatus.ARCHIVED)
        assertTrue(restored.quantity.isZero)
        assertEquals(PackageStatus.ARCHIVED, restored.status)
    }

    @Test
    fun renamedKitIsTheSameKit() {
        val created = MedKit.create(HOME_KIT, "Домашняя", null, Instant.EPOCH)
        val renamed = MedKit.restore(
            id = HOME_KIT,
            name = "Дачная",
            location = "верхняя полка",
            publication = KitPublication.LOCAL,
            participantCount = 1,
            createdAt = Instant.EPOCH,
            syncedAt = null
        )
        assertEquals(created, renamed)
        assertEquals(created.hashCode(), renamed.hashCode())
    }

    @Test
    fun newKitIsLocalWithASingleParticipant() {
        val created = MedKit.create(HOME_KIT, "Домашняя", null, Instant.EPOCH)
        assertEquals(KitPublication.LOCAL, created.publication)
        assertEquals(1L, created.participantCount)
        assertNull(created.syncedAt)
    }
}
