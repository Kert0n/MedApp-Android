package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Money

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.withShared
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets

import java.math.BigDecimal
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переходы, меняющие сведения и принадлежность. Состояний жизни у коробки нет: она либо есть, либо
 * её нет, и утрата доступа — запись в истории о том, что из учёта ушло (PLAN D3, D7). Статус говорит
 * только о решении, которое ещё не подтверждено, и помеченной к уходу коробкой не пользуются (E1).
 */
class PackageStateTransitionsTest {

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
        val emptied = factsOf(filled).withShared(category = null).copy(expiresOn = null)
        val cleared = filled.describe(emptied)
        assertNull(cleared.facts.category)
        assertNull(cleared.facts.expiresOn)
    }

    @Test
    fun editDoesNotTouchQuantityOrOwnership() {
        val moved = pack(quantity = tablets("20"))
        val described = moved.describe(factsOf(moved).withShared(name = "другое"))
        assertEquals(tablets("20"), described.quantity)
        assertEquals(HOME_KIT, described.medKit.id)
    }

    @Test
    fun movingChangesOnlyTheKit() {
        val moved = pack(claims = Claims(BigDecimal("5"))).moveTo(medKit(id = SHARED_KIT, name = "Общая").ref)
        assertEquals(SHARED_KIT, moved.medKit.id)
        assertEquals(BigDecimal("5"), moved.claims?.total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun movingIntoTheSameKitIsRefused() {
        pack(medKit = medKit(id = HOME_KIT).ref).moveTo(medKit(id = HOME_KIT).ref)
    }

    @Test
    fun losingAccessWritesWhatWasLeftIntoTheHistory() {
        // Коробка цела, но не у нас: последний виденный остаток уходит из учёта записью, и
        // она держится за ссылку на пачку, а не за саму пачку — той больше не будет.
        val movementId = Uuid.random()
        val lost = pack(quantity = tablets("7")).lost(movementId, LATER)
        assertEquals(StockMovement.AccessLoss(movementId, pack().ref, tablets("7"), observedAt = LATER), lost.trace)
    }

    @Test
    fun aBoxMarkedToGoIsReadOnly() {
        for (marked in listOf(pack().markRemoving(), pack().markLost())) {
            assertTrue(marked.take(Dose(tablets("1")), LATER).isFailure)
            assertThrows(IllegalStateException::class.java) { marked.dispose(tablets("1"), Uuid.random(), LATER) }
            assertThrows(IllegalStateException::class.java) { marked.correctTo(tablets("5"), Uuid.random(), LATER) }
            assertThrows(IllegalStateException::class.java) { marked.describe(factsOf(marked)) }
            assertThrows(IllegalStateException::class.java) { marked.moveTo(medKit(id = SHARED_KIT).ref) }
        }
    }

    @Test
    fun aBoxBeingChangedIsStillInUseAndTheAnswerSettlesIt() {
        val changing = pack().markChanging()
        assertEquals(PackageStatus.CHANGING, changing.status)
        assertTrue(changing.take(Dose(tablets("1")), LATER).isSuccess)
        assertEquals(PackageStatus.ACTIVE, changing.settled().status)
    }

    @Test
    fun theEndOfAMarkedBoxIsNotRefused() {
        // Конец — ответ на решение, а не пользование: помеченная коробка обязана уметь кончиться.
        assertEquals(pack().id, pack().markRemoving().thrownOut(Uuid.random(), LATER).record.id)
    }
}
