package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

class StockAdjustmentTest {

    @Test
    fun consumptionAndArrivalDifferOnlyBySign() {
        assertEquals(-1, adjustment(delta = BigDecimal("-2")).delta.signum())
        assertEquals(1, adjustment(kind = AdjustmentKind.INITIAL, delta = BigDecimal("20")).delta.signum())
    }

    @Test
    fun transferNamesBothKits() {
        val moved = adjustment(
            kind = AdjustmentKind.TRANSFER_OUT,
            from = HOME_KIT,
            to = SHARED_KIT
        )
        assertEquals(HOME_KIT, moved.fromMedKitId)
        assertEquals(SHARED_KIT, moved.toMedKitId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferWithoutTheSourceKitIsRejected() {
        // Иначе перенос неотличим от расхода, и история перестаёт сходиться.
        adjustment(kind = AdjustmentKind.TRANSFER_IN, from = null, to = SHARED_KIT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun kitsOfATransferAreNotFilledForOtherKinds() {
        adjustment(kind = AdjustmentKind.DISPOSAL, from = HOME_KIT, to = SHARED_KIT)
    }

    @Test
    fun momentOfSomeoneElsesChangeMayBeUnknown() {
        assertEquals(null, adjustment(kind = AdjustmentKind.REMOTE_CHANGE, occurredAt = null).occurredAt)
        assertEquals(null, adjustment(kind = AdjustmentKind.ACCESS_LOST, occurredAt = null).occurredAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun momentOfOurOwnChangeIsAlwaysKnown() {
        adjustment(kind = AdjustmentKind.CORRECTION, occurredAt = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sevenFractionDigitsAreRejected() {
        adjustment(delta = BigDecimal("-0.0000001"))
    }

    private fun adjustment(
        kind: AdjustmentKind = AdjustmentKind.DISPOSAL,
        delta: BigDecimal = BigDecimal("-1"),
        from: Uuid? = null,
        to: Uuid? = null,
        occurredAt: Instant? = Instant.EPOCH
    ) = StockAdjustment(
        id = Uuid.parse("00000000-0000-4000-8000-000000000031"),
        packageId = PACK,
        kind = kind,
        delta = delta,
        unitId = TABLETS,
        medKitId = HOME_KIT,
        fromMedKitId = from,
        toMedKitId = to,
        occurredAt = occurredAt,
        observedAt = Instant.EPOCH,
        operationId = null,
        note = null
    )
}
