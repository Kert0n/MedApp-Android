package com.kert0n.medapp.storage.pack

import com.kert0n.medapp.domain.value.Money
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.fileDatabase
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.reopenFileDatabase
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.storage.database.MedAppDatabase
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Локальные сведения об упаковке переживают снимок сервера: серверная часть переписывается
 * целиком, а строка деталей своя и создаётся всегда (PLAN F1, E4).
 */
class PackageDaoTest {

    private lateinit var database: MedAppDatabase
    private val packages get() = database.packages()

    private val local = pack(
        quantity = tablets("20"),
        expiresOn = expiry("2027-03-31"),
        defaultIntakeAmount = dose("0.5"),
        note = "в верхнем ящике",
        price = Money(BigDecimal("199.90"))
    )

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savedPackageComesBackWholeFromTwoTables() = runTest {
        packages.save(local.toStorageEntity(), local.toDetailsStorageEntity())
        val restored = requireNotNull(packages.find(PACK)).toDomain()
        assertEquals(local.facts, restored.facts)
        assertEquals(local.quantity, restored.quantity)
        assertEquals(local.addedAt, restored.addedAt)
    }

    @Test
    fun snapshotOfAnUnknownPackageCreatesItsDetailsRow() = runTest {
        val observed = Instant.parse("2026-09-10T12:00:00Z")
        packages.applyServerSnapshot(local.toStorageEntity(), observed)
        val restored = requireNotNull(packages.find(PACK)).toDomain()
        assertEquals(observed, restored.addedAt)
        assertNull(restored.facts.expiresOn)
        assertNull(restored.facts.note)
    }

    /** Повторный снимок меняет серверные поля и не трогает срок годности, заметку и цену. */
    @Test
    fun repeatedServerSnapshotKeepsLocalDetails() = runTest {
        packages.save(local.toStorageEntity(), local.toDetailsStorageEntity())

        val fromServer = local.correctTo(tablets("12")).describe(
            local.facts.copy(shared = local.facts.shared.copy(name = "Paracetamol"))
        )
        packages.applyServerSnapshot(
            fromServer.toStorageEntity(),
            Instant.parse("2026-09-11T12:00:00Z")
        )

        val restored = requireNotNull(packages.find(PACK)).toDomain()
        assertEquals("Paracetamol", restored.name)
        assertEquals(tablets("12"), restored.quantity)
        assertEquals(local.facts.expiresOn, restored.facts.expiresOn)
        assertEquals(local.facts.note, restored.facts.note)
        assertEquals(local.facts.price, restored.facts.price)
        assertEquals(local.facts.defaultIntakeAmount, restored.facts.defaultIntakeAmount)
        assertEquals(local.addedAt, restored.addedAt)
    }

    @Test
    fun packagesOfAMedKitAreObservable() = runTest {
        packages.save(local.toStorageEntity(), local.toDetailsStorageEntity())
        val other = pack(id = OTHER_PACK, name = "Ибупрофен")
        packages.save(other.toStorageEntity(), other.toDetailsStorageEntity())

        val seen = packages.observeOfMedKit(HOME_KIT).first().map { it.toDomain().name }
        assertEquals(listOf("Ибупрофен", "Парацетамол"), seen)
    }

    @Test
    fun writtenPackageSurvivesClosingTheDatabase() = runTest {
        val name = "survives.db"
        val first = fileDatabase(name)
        first.packages().save(local.toStorageEntity(), local.toDetailsStorageEntity())
        first.close()

        val second = reopenFileDatabase(name)
        try {
            val restored = requireNotNull(second.packages().find(PACK)).toDomain()
            assertEquals(local.facts, restored.facts)
            assertEquals(local.quantity, restored.quantity)
        } finally {
            second.close()
        }
    }
}
