package com.kert0n.medapp.storage.stock

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY

/**
 * Все шесть видов движения записываются одной таблицей и читаются обратно теми же самыми:
 * дискриминатор в колонке, варианты — в своём корне (PLAN D7, F1).
 */
class StockMovementStorageMapperTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")
    private val occurred: Instant = Instant.EPOCH
    private val observed: Instant = LATER

    private val everyKind: List<StockMovement> = listOf(
        StockMovement.Receipt(id, PACK, tablets("20"), HOME_KIT, occurred, observed, "куплено"),
        StockMovement.Recount(id, PACK, tablets("20"), tablets("18.5"), HOME_KIT, occurred, observed),
        StockMovement.Disposal(
            id, PACK, tablets("3"), StockMovement.Disposal.Reason.EXPIRED, HOME_KIT, occurred, observed
        ),
        StockMovement.Transfer(id, PACK, tablets("5"), HOME_KIT, SHARED_KIT, occurred, observed),
        StockMovement.RemoteChange(id, PACK, BigDecimal("-2.5"), TABLETS, SHARED_KIT, observed),
        StockMovement.AccessLoss(id, PACK, tablets("7"), SHARED_KIT, observed)
    )

    @Test
    fun everyKindSurvivesTheRoundTrip() {
        for (movement in everyKind) {
            assertEquals(movement, movement.toStorageEntity().toDomain(VOCABULARY))
        }
    }

    @Test
    fun kindIsWrittenAsItsOwnCode() {
        assertEquals(
            listOf("RECEIPT", "RECOUNT", "DISPOSAL", "TRANSFER", "REMOTE_CHANGE", "ACCESS_LOSS"),
            everyKind.map { it.toStorageEntity().kind.name }
        )
    }

    /** Перенос — одна запись с двумя концами: знак в отчёте берётся из аптечки, а не из строк. */
    @Test
    fun transferKeepsBothEndsInOneRow() {
        val transfer = everyKind.filterIsInstance<StockMovement.Transfer>().single()
        val stored = transfer.toStorageEntity()

        assertEquals(HOME_KIT, stored.sourceMedKitId)
        assertEquals(SHARED_KIT, stored.targetMedKitId)
        assertNull(stored.medKitId)

        val restored = stored.toDomain(VOCABULARY)
        assertEquals(BigDecimal("-5"), restored.deltaIn(medKit(id = HOME_KIT)))
        assertEquals(BigDecimal("5"), restored.deltaIn(medKit(id = SHARED_KIT)))
    }

    /** У чужого изменения и утраты доступа момента события нет: мы знаем только, когда узнали. */
    @Test
    fun unknownMomentStaysUnknown() {
        val remote = everyKind.filterIsInstance<StockMovement.RemoteChange>().single()
        val lost = everyKind.filterIsInstance<StockMovement.AccessLoss>().single()

        assertNull(remote.toStorageEntity().occurredAt)
        assertNull(lost.toStorageEntity().occurredAt)
        assertEquals(observed, remote.toStorageEntity().observedAt)
    }

    @Test
    fun recountKeepsBothSidesAndNotTheDifference() {
        val recount = everyKind.filterIsInstance<StockMovement.Recount>().single()
        val stored = recount.toStorageEntity()

        assertEquals("20", stored.beforeAmount)
        assertEquals("18.5", stored.afterAmount)
        assertNull(stored.delta)
        assertEquals(BigDecimal("-1.5"), stored.toDomain(VOCABULARY).deltaIn(medKit(id = HOME_KIT)))
    }
}
