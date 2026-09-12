package com.kert0n.medapp.feature.packs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKitRepository
import com.kert0n.medapp.fixture.movementRepository
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Заведение упаковки на настоящей базе: пачка и её приход ложатся одной транзакцией (PLAN F5),
 * а «некуда писать» остаётся без следов вовсе.
 */
@RunWith(AndroidJUnit4::class)
class PackageCreationTest {

    private lateinit var database: MedAppDatabase
    private lateinit var creation: PackageCreation

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        creation = PackageCreation(
            packages = database.packageRepository(),
            medKits = database.medKitRepository(),
            movements = database.movementRepository(),
            transactions = database.transactions(),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() = database.close()

    private fun facts(name: String = "Парацетамол") =
        PackageFacts(shared = PackageSharedFacts(name = name))

    /**
     * Красная проверка: не писать приход — история пачки начинается с остатка, взявшегося
     * ниоткуда, и случай краснеет.
     */
    @Test
    fun aPackageAndItsReceiptAppearTogether() = runTest {
        val id = creation.create(HOME_KIT, facts(), tablets("20"))

        val written = database.packageRepository().find(requireNotNull(id))
        assertEquals("Парацетамол", written?.name)
        assertEquals(tablets("20"), written?.quantity)
        assertEquals(now, written?.addedAt)
        val receipt = database.movementRepository().ofPackage(id).single() as StockMovement.Receipt
        assertEquals(tablets("20"), receipt.amount)
        assertEquals(now, receipt.occurredAt)
    }

    /** Аптечки нет — писать некуда: ни пачки, ни движения (PLAN F5). */
    @Test
    fun aPackageWithoutItsMedKitIsNotWritten() = runTest {
        val gone = Uuid.random()

        val id = creation.create(gone, facts(), tablets("20"))

        assertNull(id)
        assertTrue(database.stockMovements().observedBetween(Instant.EPOCH, now.plusSeconds(1)).isEmpty())
    }

    /** Каждая коробка учитывается отдельно: покупка второй не пополняет первую (AGENTS). */
    @Test
    fun twoPackagesOfTheSameNameStayTwo() = runTest {
        val first = creation.create(HOME_KIT, facts(), tablets("20"))
        val second = creation.create(HOME_KIT, facts(), tablets("20"))

        assertNotEquals(first, second)
        assertEquals(tablets("20"), database.packageRepository().find(requireNotNull(first))?.quantity)
        assertEquals(tablets("20"), database.packageRepository().find(requireNotNull(second))?.quantity)
    }
}
