package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переходы упаковки. Кончившаяся пачка архивируется, а не исчезает: иначе история приёмов за
 * прошлый месяц оборвалась бы вместе с ней (PLAN D3).
 */
class PackageTransitionsTest {

    @Test
    fun consumingToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("2")).consume(tablets("2"))
        assertTrue(empty.quantity.isZero)
        assertEquals(PackageStatus.ARCHIVED, empty.status)
    }

    @Test
    fun consumingPartOfThePackKeepsItActive() {
        val left = pack(quantity = tablets("20")).consume(tablets("0.5"))
        assertEquals(tablets("19.5"), left.quantity)
        assertEquals(PackageStatus.ACTIVE, left.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingMoreThanIsLeftIsRefused() {
        pack(quantity = tablets("3")).consume(tablets("5"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingNothingIsNotAnIntake() {
        pack().consume(Quantity.zero(TABLETS))
    }

    @Test
    fun recountToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("20")).correctTo(Quantity.zero(TABLETS))
        assertEquals(PackageStatus.ARCHIVED, empty.status)
    }

    @Test
    fun recountMayFindMoreThanWasKnown() {
        // Пересчёт — замена значения, а не дельта: пачку могли докупить или ошибиться в учёте.
        val more = pack(quantity = tablets("3")).correctTo(tablets("12"))
        assertEquals(tablets("12"), more.quantity)
        assertEquals(PackageStatus.ACTIVE, more.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountDoesNotChangeTheUnit() {
        pack(quantity = tablets("20")).correctTo(millilitres("20"))
    }

    @Test(expected = IllegalStateException::class)
    fun recountDoesNotReviveAnArchivedPack() {
        // «Удалена человеком» не отменяется числом.
        pack(status = PackageStatus.ARCHIVED).correctTo(tablets("5"))
    }

    @Test(expected = IllegalStateException::class)
    fun archivedPackIsNotConsumed() {
        pack(status = PackageStatus.ARCHIVED).consume(tablets("1"))
    }

    @Test(expected = IllegalStateException::class)
    fun inaccessiblePackIsNotEdited() {
        pack(status = PackageStatus.INACCESSIBLE).describe(editOf(pack()))
    }

    @Test(expected = IllegalStateException::class)
    fun inaccessiblePackIsNotMoved() {
        pack(status = PackageStatus.INACCESSIBLE).moveTo(SHARED_KIT)
    }

    @Test
    fun editReplacesTheWholeDescriptiveState() {
        val described = pack().describe(
            editOf(pack()).copy(
                name = "Парацетамол-Дарница",
                category = "жаропонижающие",
                expiresOn = LocalDate.of(2027, 3, 31),
                note = "в машине",
                price = Money(BigDecimal("120.00"))
            )
        )
        assertEquals("Парацетамол-Дарница", described.name)
        assertEquals("жаропонижающие", described.category)
        assertEquals(LocalDate.of(2027, 3, 31), described.expiresOn)
        assertEquals("в машине", described.note)
        assertEquals(Money(BigDecimal("120.00")), described.price)
    }

    @Test
    fun editClearsWhatWasCleared() {
        val filled = pack(category = "жаропонижающие", expiresOn = LocalDate.of(2027, 3, 31))
        val cleared = filled.describe(editOf(filled).copy(category = null, expiresOn = null))
        assertNull(cleared.category)
        assertNull(cleared.expiresOn)
    }

    @Test
    fun editDoesNotTouchQuantityOrOwnership() {
        val moved = pack(quantity = tablets("20"), version = 3)
        val described = moved.describe(editOf(moved).copy(name = "другое"))
        assertEquals(tablets("20"), described.quantity)
        assertEquals(HOME_KIT, described.medKitId)
        assertEquals(3L, described.version)
    }

    @Test
    fun movingChangesOnlyTheKit() {
        val moved = pack(version = 3, claims = Claims(BigDecimal("5"), null, 1)).moveTo(SHARED_KIT)
        assertEquals(SHARED_KIT, moved.medKitId)
        assertEquals(3L, moved.version)
    }

    @Test(expected = IllegalArgumentException::class)
    fun movingIntoTheSameKitIsRefused() {
        pack(medKitId = HOME_KIT).moveTo(HOME_KIT)
    }

    @Test
    fun archivingTwiceIsNotAnError() {
        val archived = pack().archive()
        assertSame(archived, archived.archive())
    }

    @Test
    fun archivingAnInaccessiblePackRemovesItFromTheList() {
        assertEquals(
            PackageStatus.ARCHIVED,
            pack(status = PackageStatus.INACCESSIBLE).archive().status
        )
    }

    @Test
    fun losingAccessDropsTheClaimsSnapshot() {
        // Сервер снял брони каскадом по участию: держать их снимок значило бы показывать
        // чужие брони на пачке, которой у нас больше нет.
        val lost = pack(claims = Claims(BigDecimal("5"), BigDecimal("2"), 4), version = 3).loseAccess()
        assertEquals(PackageStatus.INACCESSIBLE, lost.status)
        assertNull(lost.claims)
        assertEquals(3L, lost.version)
    }

    @Test
    fun losingAccessTwiceIsNotAnError() {
        val lost = pack().loseAccess()
        assertSame(lost, lost.loseAccess())
    }

    @Test(expected = IllegalStateException::class)
    fun archivedPackDoesNotLoseAccess() {
        pack(status = PackageStatus.ARCHIVED).loseAccess()
    }
}
