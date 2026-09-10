package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.medKit
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
    private val home = medKit(id = HOME_KIT)

    private val shared = medKit(id = SHARED_KIT, name = "Общая")

    /** Третья аптечка: в её отчёте перенос между двумя другими не виден вовсе. */
    private val elsewhere = medKit(
        id = Uuid.parse("00000000-0000-4000-8000-000000000039"),
        name = "Дачная"
    )
    private val moment: Instant = Instant.EPOCH

    private fun transfer(
        source: Uuid = HOME_KIT,
        target: Uuid = SHARED_KIT
    ) = StockMovement.Transfer(
        id, PACK, tablets("20"),
        sourceMedKitId = source, targetMedKitId = target,
        occurredAt = moment, observedAt = moment
    )

    private fun disposal(note: String? = null) = StockMovement.Disposal(
        id, PACK, tablets("2"), StockMovement.Disposal.Reason.EXPIRED, HOME_KIT,
        occurredAt = moment, observedAt = moment, note = note
    )

    @Test
    fun receiptAndDisposalDifferBySignWithoutTheCallerChoosingIt() {
        val added = StockMovement.Receipt(id, PACK, tablets("20"), HOME_KIT, moment, moment)
        assertEquals(BigDecimal("20"), added.deltaIn(home))
        assertEquals(BigDecimal("-2"), disposal().deltaIn(home))
    }

    @Test
    fun movementChangesOnlyItsOwnKit() {
        assertEquals(BigDecimal.ZERO, disposal().deltaIn(shared))
    }

    @Test
    fun unitComesFromTheAmountAndCannotContradictIt() {
        val added = StockMovement.Receipt(id, PACK, millilitres("100"), HOME_KIT, moment, moment)
        assertEquals(MILLILITRES, added.unitId)
    }

    @Test
    fun transferIsOneRecordWithTwoEnds() {
        val moved = transfer()
        assertEquals(BigDecimal("-20"), moved.deltaIn(home))
        assertEquals(BigDecimal("20"), moved.deltaIn(shared))
        assertEquals(BigDecimal.ZERO, moved.deltaIn(elsewhere))
    }

    @Test
    fun transferInsideTheSelectedKitsIsNotConsumption() {
        // Отчёт по двум аптечкам складывает их изменения: перекладывание пачки даёт ноль (H6).
        val moved = transfer()
        assertEquals(0, (moved.deltaIn(home) + moved.deltaIn(shared)).signum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferIntoTheSameKitIsRejected() {
        // Остаток от такого переноса не меняется, а в истории он выглядел бы событием.
        transfer(source = HOME_KIT, target = HOME_KIT)
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
        assertEquals(BigDecimal("9"), found.deltaIn(home))
        assertEquals(BigDecimal("-9"), lost.deltaIn(home))
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
        assertEquals(BigDecimal("3"), up.deltaIn(home))
        assertEquals(BigDecimal("-3"), down.deltaIn(home))
        assertNull(up.occurredAt)
    }

    @Test
    fun accessLossTakesTheWholeRemainderOut() {
        val lost = StockMovement.AccessLoss(id, PACK, tablets("7"), SHARED_KIT, observedAt = moment)
        assertEquals(BigDecimal("-7"), lost.deltaIn(shared))
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
