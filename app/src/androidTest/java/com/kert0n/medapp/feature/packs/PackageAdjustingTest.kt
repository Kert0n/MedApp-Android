package com.kert0n.medapp.feature.packs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKitRepository
import com.kert0n.medapp.fixture.movementRepository
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.transactions
import com.kert0n.medapp.storage.database.MedAppDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Пересчёт, утилизация и перенос на настоящей базе: переход и его след ложатся одной транзакцией,
 * и «было» в истории — то, что лежало в базе, а не то, что экран прочитал раньше (PLAN F5).
 */
@RunWith(AndroidJUnit4::class)
class PackageAdjustingTest {

    private lateinit var database: MedAppDatabase
    private lateinit var adjusting: PackageAdjusting

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        adjusting = PackageAdjusting(
            packages = database.packageRepository(),
            medKits = database.medKitRepository(),
            transactions = database.transactions(),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun stored(quantity: String = "20") {
        database.packageRepository().add(pack(id = PACK, quantity = tablets(quantity)))
    }

    /** Локальный пересчёт меняет показанный остаток ровно один раз (PLAN I, проверки PR 7). */
    @Test
    fun aRecountChangesTheAmountExactlyOnce() = runTest {
        stored()

        assertTrue(adjusting.recount(PACK, tablets("17")))

        assertEquals(tablets("17"), database.packageRepository().find(PACK)?.quantity)
        val movement = database.movementRepository().ofPackage(PACK).single() as StockMovement.Recount
        assertEquals(tablets("20"), movement.before)
        assertEquals(tablets("17"), movement.after)
    }

    @Test
    fun aDisposalToZeroArchivesThePackageAndKeepsTheHistory() = runTest {
        stored()

        adjusting.dispose(PACK, tablets("20"), StockMovement.Disposal.Reason.EXPIRED)

        assertEquals(Package.Lifecycle.ARCHIVED, database.packageRepository().find(PACK)?.lifecycle)
        assertEquals(1, database.movementRepository().ofPackage(PACK).size)
    }

    /**
     * Перенос между местными аптечками — одна транзакция: читается аптечка назначения и
     * переставляется место (PLAN E6). Истории он не касается: истрачено ничего не было (D7).
     */
    @Test
    fun aLocalTransferMovesThePackageAndSpendsNothing() = runTest {
        stored()

        assertTrue(adjusting.moveTo(PACK, SHARED_KIT))

        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(tablets("20"), database.packageRepository().find(PACK)?.quantity)
        assertTrue(database.movementRepository().ofPackage(PACK).isEmpty())
    }

    /** Аптечки назначения нет — переносить некуда, и не записано ничего. */
    @Test
    fun aTransferToNowhereWritesNothing() = runTest {
        stored()

        assertFalse(adjusting.moveTo(PACK, Uuid.random()))

        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertTrue(database.movementRepository().ofPackage(PACK).isEmpty())
    }
}
