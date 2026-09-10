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
        assertEquals(PackageLifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun consumingPartOfThePackKeepsItActive() {
        val left = pack(quantity = tablets("20")).consume(tablets("0.5"))
        assertEquals(tablets("19.5"), left.quantity)
        assertEquals(PackageLifecycle.ACTIVE, left.lifecycle)
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
        assertEquals(PackageLifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun recountMayFindMoreThanWasKnown() {
        // Пересчёт — замена значения, а не дельта: пачку могли докупить или ошибиться в учёте.
        val more = pack(quantity = tablets("3")).correctTo(tablets("12"))
        assertEquals(tablets("12"), more.quantity)
        assertEquals(PackageLifecycle.ACTIVE, more.lifecycle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountDoesNotChangeTheUnit() {
        pack(quantity = tablets("20")).correctTo(millilitres("20"))
    }

    @Test(expected = IllegalStateException::class)
    fun recountDoesNotReviveAnArchivedPack() {
        // «Удалена человеком» не отменяется числом.
        pack(lifecycle = PackageLifecycle.ARCHIVED).correctTo(tablets("5"))
    }

    @Test(expected = IllegalStateException::class)
    fun archivedPackIsNotConsumed() {
        pack(lifecycle = PackageLifecycle.ARCHIVED).consume(tablets("1"))
    }

    @Test(expected = IllegalStateException::class)
    fun inaccessiblePackIsNotEdited() {
        pack(access = PackageAccess.LOST).describe(factsOf(pack()))
    }

    @Test(expected = IllegalStateException::class)
    fun inaccessiblePackIsNotMoved() {
        pack(access = PackageAccess.LOST).moveTo(SHARED_KIT)
    }

    @Test
    fun editReplacesTheWholeDescriptiveState() {
        val described = pack().describe(
            factsOf(pack()).copy(
                name = "Парацетамол-Дарница",
                category = "жаропонижающие",
                expiresOn = LocalDate.of(2027, 3, 31),
                note = "в машине",
                price = Money(BigDecimal("120.00"))
            )
        )
        assertEquals("Парацетамол-Дарница", described.name)
        assertEquals("жаропонижающие", described.facts.category)
        assertEquals(LocalDate.of(2027, 3, 31), described.facts.expiresOn)
        assertEquals("в машине", described.facts.note)
        assertEquals(Money(BigDecimal("120.00")), described.facts.price)
    }

    @Test
    fun editClearsWhatWasCleared() {
        val filled = pack(category = "жаропонижающие", expiresOn = LocalDate.of(2027, 3, 31))
        val cleared = filled.describe(factsOf(filled).copy(category = null, expiresOn = null))
        assertNull(cleared.facts.category)
        assertNull(cleared.facts.expiresOn)
    }

    @Test
    fun editDoesNotTouchQuantityOrOwnership() {
        val moved = pack(quantity = tablets("20"))
        val described = moved.describe(factsOf(moved).copy(name = "другое"))
        assertEquals(tablets("20"), described.quantity)
        assertEquals(HOME_KIT, described.medKitId)
    }

    @Test
    fun movingChangesOnlyTheKit() {
        val moved = pack(claims = Claims(BigDecimal("5"))).moveTo(SHARED_KIT)
        assertEquals(SHARED_KIT, moved.medKitId)
        assertEquals(BigDecimal("5"), moved.claims?.total)
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
            PackageLifecycle.ARCHIVED,
            pack(access = PackageAccess.LOST).archive().lifecycle
        )
    }

    @Test
    fun losingAccessDropsTheClaimsSnapshot() {
        // Сервер снял брони каскадом по участию: держать их снимок значило бы показывать
        // чужие брони на пачке, которой у нас больше нет.
        val shared = pack(claims = Claims(BigDecimal("5"), BigDecimal("2")))
        val lost = shared.loseAccess()
        assertEquals(PackageAccess.LOST, lost.access)
        assertEquals(PackageLifecycle.ACTIVE, lost.lifecycle)
        assertNull(lost.claims)
    }

    @Test
    fun losingAccessTwiceIsNotAnError() {
        val lost = pack().loseAccess()
        assertSame(lost, lost.loseAccess())
    }

    @Test
    fun archivedPackCanAlsoLoseAccess() {
        // Две оси, а не одна: выбросить свою часть общей пачки и потом выйти из аптечки — это
        // два разных события, и оба остаются записанными.
        val lost = pack(quantity = tablets("2")).consume(tablets("2")).loseAccess()
        assertEquals(PackageLifecycle.ARCHIVED, lost.lifecycle)
        assertEquals(PackageAccess.LOST, lost.access)
    }

    @Test
    fun losingAccessKeepsWhatWasLeft() {
        // Остаток недоступной пачки помним: он нужен движению ACCESS_LOST и отчёту.
        val lost = pack(quantity = tablets("7")).loseAccess()
        assertEquals(tablets("7"), lost.quantity)
    }
}
