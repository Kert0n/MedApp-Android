package com.kert0n.medapp.domain.stock

import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Движение — запись о пачке: что с ней стало, сколько это было и когда (PLAN D7). Места в записи
 * нет: продукт спрашивает «сколько истрачено» (ТЗ 4.1.1.10.2), и аптечка на этот ответ не влияет.
 */
class StockMovementTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000031")

    private val moment: Instant = Instant.EPOCH

    /** Пачка — ссылкой: движение называет её саму, а не номер. */
    private val paracetamol = pack(id = PACK)

    private fun disposal(note: String? = null) = StockMovement.Disposal(
        id, paracetamol.ref, tablets("2"), StockMovement.Disposal.Reason.EXPIRED,
        occurredAt = moment, observedAt = moment, note = note
    )

    /** Единица берётся у количества и разойтись с ним не может: второго поля для неё нет. */
    @Test
    fun unitComesFromTheAmountAndCannotContradictIt() {
        val added = StockMovement.Receipt(id, paracetamol.ref, millilitres("100"), moment, moment)

        assertEquals(MILLILITRES, added.unit)
    }

    /**
     * Пересчёт принимает оба остатка, а не разницу: «было 3, стало 12» — это факт, а вычитание из
     * него получается само (PLAN E1).
     */
    @Test
    fun recountKeepsBothEndsOfWhatWasSeen() {
        val found = StockMovement.Recount(
            id, paracetamol.ref, before = tablets("3"), after = tablets("12"),
            occurredAt = moment, observedAt = moment
        )

        assertEquals(tablets("3"), found.before)
        assertEquals(tablets("12"), found.after)
        assertEquals(TABLETS, found.unit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountAcrossUnitsIsRejected() {
        StockMovement.Recount(
            id, paracetamol.ref, before = tablets("3"), after = millilitres("3"),
            occurredAt = moment, observedAt = moment
        )
    }

    /** Чужое изменение бывает в обе стороны, и когда оно случилось, мы не знаем (PLAN D7). */
    @Test
    fun remoteChangeIsSignedAndItsMomentMayBeUnknown() {
        val down = StockMovement.RemoteChange(
            id, paracetamol.ref, BigDecimal("-3"), TABLETS, observedAt = moment
        )

        assertEquals(BigDecimal("-3"), down.delta)
        assertNull(down.occurredAt)
    }

    @Test
    fun accessLossRemembersTheLastSeenRemainder() {
        val lost = StockMovement.AccessLoss(id, paracetamol.ref, tablets("7"), observedAt = moment)

        assertEquals(tablets("7"), lost.amount)
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
            id, paracetamol.ref, BigDecimal("-0.0000001"), TABLETS, observedAt = moment
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongNoteIsRejected() {
        disposal(note = "я".repeat(StockMovement.NOTE_MAX_LENGTH + 1))
    }
}
