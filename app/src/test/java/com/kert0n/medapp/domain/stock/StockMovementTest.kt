package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Знак и аптечка движения получаются из его вида, а перенос — одна запись с двумя концами. */
class StockMovementTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000031")
    private val elsewhere: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000039")
    private val moment: Instant = Instant.EPOCH

    private fun transfer(from: Uuid = HOME_KIT, to: Uuid = SHARED_KIT) = StockMovement.Transfer(
        id, PACK, tablets("20"), from = from, to = to, occurredAt = moment, observedAt = moment
    )

    private fun disposal(note: String? = null) = StockMovement.Disposal(
        id, PACK, tablets("2"), StockMovement.Disposal.Reason.EXPIRED, HOME_KIT,
        occurredAt = moment, observedAt = moment, note = note
    )

    @Test
    fun receiptAndDisposalDifferBySignWithoutTheCallerChoosingIt() {
        val added = StockMovement.Receipt(id, PACK, tablets("20"), HOME_KIT, moment, moment)
        assertEquals(BigDecimal("20"), added.deltaIn(HOME_KIT))
        assertEquals(BigDecimal("-2"), disposal().deltaIn(HOME_KIT))
    }

    @Test
    fun movementChangesOnlyItsOwnKit() {
        assertEquals(BigDecimal.ZERO, disposal().deltaIn(SHARED_KIT))
    }

    @Test
    fun unitComesFromTheAmountAndCannotContradictIt() {
        val added = StockMovement.Receipt(id, PACK, millilitres("100"), HOME_KIT, moment, moment)
        assertEquals(MILLILITRES, added.unitId)
    }

    @Test
    fun transferIsOneRecordWithTwoEnds() {
        val moved = transfer()
        assertEquals(BigDecimal("-20"), moved.deltaIn(HOME_KIT))
        assertEquals(BigDecimal("20"), moved.deltaIn(SHARED_KIT))
        assertEquals(BigDecimal.ZERO, moved.deltaIn(elsewhere))
    }

    @Test
    fun transferInsideTheSelectedKitsIsNotConsumption() {
        // Отчёт по двум аптечкам складывает их изменения: перекладывание пачки даёт ноль (H6).
        val moved = transfer()
        assertEquals(0, (moved.deltaIn(HOME_KIT) + moved.deltaIn(SHARED_KIT)).signum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferIntoTheSameKitIsRejected() {
        // Остаток от такого переноса не меняется, а в истории он выглядел бы событием.
        transfer(from = HOME_KIT, to = HOME_KIT)
    }

    @Test
    fun recountTakesBothAmountsAndWorksOutTheSign() {
        val found = StockMovement.Recount(
            id, PACK, before = tablets("3"), after = tablets("12"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
        val lost = StockMovement.Recount(
            id, PACK, before = tablets("12"), after = tablets("3"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
        assertEquals(BigDecimal("9"), found.deltaIn(HOME_KIT))
        assertEquals(BigDecimal("-9"), lost.deltaIn(HOME_KIT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountAcrossUnitsIsRejected() {
        StockMovement.Recount(
            id, PACK, before = tablets("3"), after = millilitres("3"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
    }

    @Test
    fun remoteChangeIsSignedAndItsMomentMayBeUnknown() {
        val up = StockMovement.RemoteChange(
            id, PACK, BigDecimal("3"), TABLETS, HOME_KIT, observedAt = moment
        )
        val down = StockMovement.RemoteChange(
            id, PACK, BigDecimal("-3"), TABLETS, HOME_KIT, observedAt = moment
        )
        assertEquals(BigDecimal("3"), up.deltaIn(HOME_KIT))
        assertEquals(BigDecimal("-3"), down.deltaIn(HOME_KIT))
        assertNull(up.occurredAt)
    }

    @Test
    fun accessLossTakesTheWholeRemainderOut() {
        val lost = StockMovement.AccessLoss(id, PACK, tablets("7"), SHARED_KIT, observedAt = moment)
        assertEquals(BigDecimal("-7"), lost.deltaIn(SHARED_KIT))
        assertNull(lost.occurredAt)
    }

    @Test
    fun ourOwnActionHasItsMomentAndItsReason() {
        // У утилизации момент — `Instant`, а не `Instant?`: «своё без момента» не собрать.
        val thrownOut = disposal()
        assertEquals(moment, thrownOut.occurredAt)
        assertEquals(StockMovement.Disposal.Reason.EXPIRED, thrownOut.reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sevenFractionDigitsAreRejected() {
        StockMovement.RemoteChange(
            id, PACK, BigDecimal("-0.0000001"), TABLETS, HOME_KIT, observedAt = moment
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongNoteIsRejected() {
        disposal(note = "я".repeat(StockMovement.NOTE_MAX_LENGTH + 1))
    }
}
