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
        val added = adjustment(kind = AdjustmentKind.INITIAL, delta = BigDecimal("20"))
        assertEquals(1, added.delta.signum())
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
        adjustment(
            kind = AdjustmentKind.TRANSFER_IN,
            delta = BigDecimal("20"),
            from = null,
            to = SHARED_KIT
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferIntoTheSameKitIsRejected() {
        // Остаток от такого переноса не меняется, а в истории он выглядел бы событием.
        adjustment(
            kind = AdjustmentKind.TRANSFER_OUT,
            from = HOME_KIT,
            to = HOME_KIT
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeArrivalIsRejected() {
        // Отрицательный приход сходится в сумме, но означает противоположное своему виду.
        adjustment(kind = AdjustmentKind.INITIAL, delta = BigDecimal("-20"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun positiveDisposalIsRejected() {
        adjustment(kind = AdjustmentKind.DISPOSAL, delta = BigDecimal("2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun positiveAccessLossIsRejected() {
        adjustment(kind = AdjustmentKind.ACCESS_LOST, delta = BigDecimal("2"), occurredAt = null)
    }

    @Test
    fun recountAndRemoteChangeGoBothWays() {
        // Пересчёт находит и больше, и меньше; причину чужого изменения мы не знаем вовсе.
        val found = adjustment(kind = AdjustmentKind.CORRECTION, delta = BigDecimal("3"))
        val lost = adjustment(kind = AdjustmentKind.CORRECTION, delta = BigDecimal("-3"))
        assertEquals(1, found.delta.signum())
        assertEquals(-1, lost.delta.signum())

        val remoteUp = adjustment(
            kind = AdjustmentKind.REMOTE_CHANGE,
            delta = BigDecimal("3"),
            occurredAt = null
        )
        val remoteDown = adjustment(
            kind = AdjustmentKind.REMOTE_CHANGE,
            delta = BigDecimal("-3"),
            occurredAt = null
        )
        assertEquals(1, remoteUp.delta.signum())
        assertEquals(-1, remoteDown.delta.signum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun kitsOfATransferAreNotFilledForOtherKinds() {
        adjustment(kind = AdjustmentKind.DISPOSAL, from = HOME_KIT, to = SHARED_KIT)
    }

    @Test
    fun momentOfSomeoneElsesChangeMayBeUnknown() {
        val remote = adjustment(kind = AdjustmentKind.REMOTE_CHANGE, occurredAt = null)
        val lost = adjustment(kind = AdjustmentKind.ACCESS_LOST, occurredAt = null)
        assertEquals(null, remote.occurredAt)
        assertEquals(null, lost.occurredAt)
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
