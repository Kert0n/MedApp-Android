package com.kert0n.medapp.storage.stock

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.toStorageRow
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Все пять видов движения записываются одной таблицей и читаются обратно теми же самыми:
 * дискриминатор в колонке, варианты — в своём корне (PLAN D7, F1).
 */
class StockMovementStorageMapperTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")
    private val occurred: Instant = Instant.EPOCH
    private val observed: Instant = LATER

    private val paracetamol = pack(id = PACK)

    private val everyKind: List<StockMovement> = listOf(
        StockMovement.Receipt(id, paracetamol.ref, tablets("20"), occurred, observed, "куплено"),
        StockMovement.Recount(id, paracetamol.ref, tablets("20"), tablets("18.5"), occurred, observed),
        StockMovement.Disposal(
            id, paracetamol.ref, tablets("3"), StockMovement.Disposal.Reason.EXPIRED, occurred, observed
        ),
        StockMovement.RemoteChange(id, paracetamol.ref, BigDecimal("-2.5"), TABLETS, observed),
        StockMovement.AccessLoss(id, paracetamol.ref, tablets("7"), observed)
    )

    @Test
    fun everyKindSurvivesTheRoundTrip() {
        for (movement in everyKind) {
            assertEquals(movement, movement.toStorageRow().toDomain(VOCABULARY))
        }
    }

    @Test
    fun kindIsWrittenAsItsOwnCode() {
        assertEquals(
            listOf("RECEIPT", "RECOUNT", "DISPOSAL", "REMOTE_CHANGE", "ACCESS_LOSS"),
            everyKind.map { it.toStorageEntity().kind.name }
        )
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

    /**
     * Пересчёт хранит оба остатка, а не их разницу: разницу всегда можно посчитать, а «было»
     * восстановить из неё нельзя.
     */
    @Test
    fun recountKeepsBothSidesAndNotTheDifference() {
        val recount = everyKind.filterIsInstance<StockMovement.Recount>().single()
        val stored = recount.toStorageEntity()

        assertEquals("20", stored.beforeAmount)
        assertEquals("18.5", stored.afterAmount)
        assertNull(stored.delta)
        assertEquals(recount, recount.toStorageRow().toDomain(VOCABULARY))
    }
}
