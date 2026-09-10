package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Money

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.withShared
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Переходы упаковки. Кончившаяся пачка архивируется, а не исчезает: иначе история приёмов за
 * прошлый месяц оборвалась бы вместе с ней (PLAN D3).
 */
/**
 * Переходы, меняющие сведения, принадлежность и две оси состояния. Жизненный цикл и доступ
 * независимы: выбросить свою часть общей пачки и выйти из аптечки можно в любом порядке (PLAN D3).
 */
class PackageStateTransitionsTest {

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
            factsOf(pack())
                .withShared(name = "Парацетамол-Дарница", category = "жаропонижающие")
                .copy(
                    expiresOn = expiry("2027-03-31"),
                    note = "в машине",
                    price = Money(BigDecimal("120.00"))
                )
        )
        assertEquals("Парацетамол-Дарница", described.name)
        assertEquals("жаропонижающие", described.facts.category)
        assertEquals(expiry("2027-03-31"), described.facts.expiresOn)
        assertEquals("в машине", described.facts.note)
        assertEquals(Money(BigDecimal("120.00")), described.facts.price)
    }

    @Test
    fun editClearsWhatWasCleared() {
        val filled = pack(category = "жаропонижающие", expiresOn = expiry("2027-03-31"))
        val cleared = filled.describe(factsOf(filled).withShared(category = null).copy(expiresOn = null))
        assertNull(cleared.facts.category)
        assertNull(cleared.facts.expiresOn)
    }

    @Test
    fun editDoesNotTouchQuantityOrOwnership() {
        val moved = pack(quantity = tablets("20"))
        val described = moved.describe(factsOf(moved).withShared(name = "другое"))
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
