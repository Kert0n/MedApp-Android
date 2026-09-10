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

/**
 * Вид движения — это тип, поэтому знак и аптечки не проверяются, а получаются. Тесты проверяют
 * именно это: неверное движение не отвергается, а не выражается.
 */
class StockAdjustmentTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000031")
    private val moment: Instant = Instant.EPOCH

    @Test
    fun arrivalAndConsumptionDifferBySignWithoutTheCallerChoosingIt() {
        val added = StockInitial(id, PACK, tablets("20"), HOME_KIT, moment, moment)
        val thrownOut = StockDisposal(id, PACK, tablets("2"), HOME_KIT, moment, moment)
        assertEquals(BigDecimal("20"), added.delta)
        assertEquals(BigDecimal("-2"), thrownOut.delta)
    }

    @Test
    fun unitComesFromTheAmountAndCannotContradictIt() {
        val added = StockInitial(id, PACK, millilitres("100"), HOME_KIT, moment, moment)
        assertEquals(MILLILITRES, added.unitId)
    }

    @Test
    fun transferOutHappensInTheSourceKit() {
        val moved = StockTransferOut(
            id, PACK, tablets("20"), from = HOME_KIT, to = SHARED_KIT,
            occurredAt = moment, observedAt = moment
        )
        assertEquals(HOME_KIT, moved.medKitId)
        assertEquals(BigDecimal("-20"), moved.delta)
    }

    @Test
    fun transferInHappensInTheDestinationKit() {
        val arrived = StockTransferIn(
            id, PACK, tablets("20"), from = HOME_KIT, to = SHARED_KIT,
            occurredAt = moment, observedAt = moment
        )
        assertEquals(SHARED_KIT, arrived.medKitId)
        assertEquals(BigDecimal("20"), arrived.delta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferIntoTheSameKitIsRejected() {
        // Остаток от такого переноса не меняется, а в истории он выглядел бы событием.
        StockTransferOut(
            id, PACK, tablets("20"), from = HOME_KIT, to = HOME_KIT,
            occurredAt = moment, observedAt = moment
        )
    }

    @Test
    fun recountTakesBothAmountsAndWorksOutTheSign() {
        val found = StockCorrection(
            id, PACK, from = tablets("3"), to = tablets("12"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
        val lost = StockCorrection(
            id, PACK, from = tablets("12"), to = tablets("3"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
        assertEquals(BigDecimal("9"), found.delta)
        assertEquals(BigDecimal("-9"), lost.delta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountAcrossUnitsIsRejected() {
        StockCorrection(
            id, PACK, from = tablets("3"), to = millilitres("3"),
            medKitId = HOME_KIT, occurredAt = moment, observedAt = moment
        )
    }

    @Test
    fun remoteChangeIsSignedAndItsMomentMayBeUnknown() {
        val up = StockRemoteChange(
            id, PACK, BigDecimal("3"), TABLETS, HOME_KIT, observedAt = moment
        )
        val down = StockRemoteChange(
            id, PACK, BigDecimal("-3"), TABLETS, HOME_KIT, observedAt = moment
        )
        assertEquals(BigDecimal("3"), up.delta)
        assertEquals(BigDecimal("-3"), down.delta)
        assertNull(up.occurredAt)
    }

    @Test
    fun accessLossTakesTheWholeRemainderOut() {
        val lost =
            StockAccessLost(id, PACK, tablets("7"), SHARED_KIT, observedAt = moment)
        assertEquals(BigDecimal("-7"), lost.delta)
        assertNull(lost.occurredAt)
    }

    @Test
    fun momentOfOurOwnActionIsRequiredByTheTypeItself() {
        // У Disposal occurredAt не Instant?, а Instant: «своё изменение без момента» не собрать,
        // и проверять это в init больше не нужно.
        val ours = StockDisposal(id, PACK, tablets("1"), HOME_KIT, moment, moment)
        assertEquals(moment, ours.occurredAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sevenFractionDigitsAreRejected() {
        StockRemoteChange(
            id, PACK, BigDecimal("-0.0000001"), TABLETS, HOME_KIT, observedAt = moment
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongNoteIsRejected() {
        StockDisposal(
            id, PACK, tablets("1"), HOME_KIT, moment, moment,
            note = "я".repeat(ADJUSTMENT_NOTE_MAX_LENGTH + 1)
        )
    }
}
