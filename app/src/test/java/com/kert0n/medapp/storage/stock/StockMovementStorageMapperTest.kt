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
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.toStorageRow

/**
 * Все шесть видов движения записываются одной таблицей и читаются обратно теми же самыми:
 * дискриминатор в колонке, варианты — в своём корне (PLAN D7, F1).
 */
class StockMovementStorageMapperTest {

    private val id: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")
    private val occurred: Instant = Instant.EPOCH
    private val observed: Instant = LATER

    private val paracetamol = pack(id = PACK)
    private val home = medKit(id = HOME_KIT)
    private val shared = medKit(id = SHARED_KIT, name = "Общая")

    private val everyKind: List<StockMovement> = listOf(
        StockMovement.Receipt(id, paracetamol.ref, tablets("20"), home.ref, occurred, observed, "куплено"),
        StockMovement.Recount(id, paracetamol.ref, tablets("20"), tablets("18.5"), home.ref, occurred, observed),
        StockMovement.Disposal(
            id, paracetamol.ref, tablets("3"), StockMovement.Disposal.Reason.EXPIRED, home.ref, occurred, observed
        ),
        StockMovement.Transfer(id, paracetamol.ref, tablets("5"), home.ref, shared.ref, occurred, observed),
        StockMovement.RemoteChange(id, paracetamol.ref, BigDecimal("-2.5"), TABLETS, shared.ref, observed),
        StockMovement.AccessLoss(id, paracetamol.ref, tablets("7"), shared.ref, observed)
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

        val restored = transfer.toStorageRow().toDomain(VOCABULARY)
        assertEquals(BigDecimal("-5"), restored.deltaIn(medKit(id = HOME_KIT).ref))
        assertEquals(BigDecimal("5"), restored.deltaIn(medKit(id = SHARED_KIT).ref))
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
        assertEquals(BigDecimal("-1.5"), recount.toStorageRow().toDomain(VOCABULARY).deltaIn(medKit(id = HOME_KIT).ref))
    }
}
