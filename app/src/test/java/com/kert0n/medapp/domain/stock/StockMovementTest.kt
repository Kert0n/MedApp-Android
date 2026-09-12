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
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.pack

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

    /** Пачка — объектом: движение называет её саму, а не номер. */
    private val paracetamol = pack(id = PACK)

    private fun transfer(
        source: MedKit = home,
        target: MedKit = shared
    ) = StockMovement.Transfer(
        id, paracetamol.ref, tablets("20"),
        source = source.ref, target = target.ref,
        occurredAt = moment, observedAt = moment
    )

    private fun disposal(note: String? = null) = StockMovement.Disposal(
        id, paracetamol.ref, tablets("2"), StockMovement.Disposal.Reason.EXPIRED, home.ref,
        occurredAt = moment, observedAt = moment, note = note
    )

    @Test
    fun receiptAndDisposalDifferBySignWithoutTheCallerChoosingIt() {
        val added = StockMovement.Receipt(id, paracetamol.ref, tablets("20"), home.ref, moment, moment)
        assertEquals(BigDecimal("20"), added.deltaIn(home.ref))
        assertEquals(BigDecimal("-2"), disposal().deltaIn(home.ref))
    }

    @Test
    fun movementChangesOnlyItsOwnKit() {
        assertEquals(BigDecimal.ZERO, disposal().deltaIn(shared.ref))
    }

    @Test
    fun unitComesFromTheAmountAndCannotContradictIt() {
        val added = StockMovement.Receipt(id, paracetamol.ref, millilitres("100"), home.ref, moment, moment)
        assertEquals(MILLILITRES, added.unit)
    }

    @Test
    fun transferIsOneRecordWithTwoEnds() {
        val moved = transfer()
        assertEquals(BigDecimal("-20"), moved.deltaIn(home.ref))
        assertEquals(BigDecimal("20"), moved.deltaIn(shared.ref))
        assertEquals(BigDecimal.ZERO, moved.deltaIn(elsewhere.ref))
    }

    @Test
    fun transferInsideTheSelectedKitsIsNotConsumption() {
        // Отчёт по двум аптечкам складывает их изменения: перекладывание пачки даёт ноль (H6).
        val moved = transfer()
        assertEquals(0, (moved.deltaIn(home.ref) + moved.deltaIn(shared.ref)).signum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferIntoTheSameKitIsRejected() {
        // Остаток от такого переноса не меняется, а в истории он выглядел бы событием.
        transfer(source = home, target = home)
    }

    @Test
    fun recountTakesBothAmountsAndWorksOutTheSign() {
        val found = StockMovement.Recount(
            id, paracetamol.ref, before = tablets("3"), after = tablets("12"),
            medKit = home.ref, occurredAt = moment, observedAt = moment
        )
        val lost = StockMovement.Recount(
            id, paracetamol.ref, before = tablets("12"), after = tablets("3"),
            medKit = home.ref, occurredAt = moment, observedAt = moment
        )
        assertEquals(BigDecimal("9"), found.deltaIn(home.ref))
        assertEquals(BigDecimal("-9"), lost.deltaIn(home.ref))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountAcrossUnitsIsRejected() {
        StockMovement.Recount(
            id, paracetamol.ref, before = tablets("3"), after = millilitres("3"),
            medKit = home.ref, occurredAt = moment, observedAt = moment
        )
    }

    @Test
    fun remoteChangeIsSignedAndItsMomentMayBeUnknown() {
        val up = StockMovement.RemoteChange(
            id, paracetamol.ref, BigDecimal("3"), TABLETS, home.ref, observedAt = moment
        )
        val down = StockMovement.RemoteChange(
            id, paracetamol.ref, BigDecimal("-3"), TABLETS, home.ref, observedAt = moment
        )
        assertEquals(BigDecimal("3"), up.deltaIn(home.ref))
        assertEquals(BigDecimal("-3"), down.deltaIn(home.ref))
        assertNull(up.occurredAt)
    }

    @Test
    fun accessLossTakesTheWholeRemainderOut() {
        val lost = StockMovement.AccessLoss(id, paracetamol.ref, tablets("7"), shared.ref, observedAt = moment)
        assertEquals(BigDecimal("-7"), lost.deltaIn(shared.ref))
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
            id, paracetamol.ref, BigDecimal("-0.0000001"), TABLETS, home.ref, observedAt = moment
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongNoteIsRejected() {
        disposal(note = "я".repeat(StockMovement.NOTE_MAX_LENGTH + 1))
    }
}
