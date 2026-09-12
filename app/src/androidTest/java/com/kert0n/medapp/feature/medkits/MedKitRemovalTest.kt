package com.kert0n.medapp.feature.medkits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.medKitRepository
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.transactions
import com.kert0n.medapp.storage.course.toStorageEntity as toRecordStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.toStorageEntity as toIntakeStorageEntity
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Человек выбрасывает аптечку (ТЗ 4.1.1.2.3). Проверяется на настоящей базе: то, ради чего всё
 * это затевалось, — что схема действительно даёт убрать аптечку, а история лечения это переживает
 * (PLAN E6, D6).
 */
@RunWith(AndroidJUnit4::class)
class MedKitRemovalTest {

    private lateinit var database: MedAppDatabase
    private lateinit var removal: MedKitRemoval

    private val movementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        removal = MedKitRemoval(
            medKits = database.medKitRepository(),
            packages = database.packageRepository(),
            transactions = database.transactions()
        )
        database.packageRepository().add(pack(id = PACK, quantity = tablets("20")))
        database.packageRepository().add(pack(id = OTHER_PACK, quantity = tablets("1")))
        database.stockMovements().insert(
            StockMovement.Receipt(movementId, pack(id = PACK).ref, tablets("20"), Instant.EPOCH, LATER)
                .toMovementStorageEntity()
        )
        database.courses().upsertRecord(courseRecord().toRecordStorageEntity())
        database.intakes().upsert(
            plannedIntake().confirm(pack(id = PACK).take(dose("2"), LATER).getOrThrow())
                .toIntakeStorageEntity()
        )
    }

    @After
    fun tearDown() = database.close()

    /**
     * «Забрал аптечку домой»: содержимое переезжает целиком — и живое, и кончившееся, — а
     * исходная аптечка уходит (ТЗ 4.1.1.2.3.2).
     */
    @Test
    fun takingTheMedKitAwayMovesEverythingAndRemovesThePlace() = runTest {
        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(SHARED_KIT, database.packageRepository().find(OTHER_PACK)?.medKit?.id)
        assertEquals(1, database.stockMovements().ofPackage(PACK).size)
    }

    /**
     * «Выбросил вместе с лекарствами» (ТЗ 4.1.1.2.3.1): коробок не остаётся, а история — запись
     * эпизода, приём и движения — переживает: она держится за записи о коробках (PLAN D3, D6).
     *
     * Красная проверка: посадить ключ приёма на живую строку — аптечку, из которой хоть раз
     * принимали, выбросить станет нельзя, и случай краснеет.
     */
    @Test
    fun throwingTheMedKitOutWithItsDrugsKeepsTheTreatment() = runTest {
        val outcome = removal.remove(HOME_KIT, transferTo = null)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertNull(database.packageRepository().find(PACK))
        assertNull(database.packageRepository().find(OTHER_PACK))
        assertEquals(1, database.stockMovements().ofPackage(PACK).size)

        assertNotNull(database.courses().findRecord(COURSE))
        val intake = requireNotNull(database.intakes().find(INTAKE)).toDomain(VOCABULARY)
        assertEquals(dose("2"), intake.taken?.amount)
        assertEquals("Парацетамол", intake.taken?.pkg?.name)
    }

    /** Общая аптечка местным решением не убирается: она есть у других людей (PLAN C3, E5). */
    @Test
    fun aSharedMedKitNeedsTheServer() = runTest {
        database.medKits().upsert(
            medKit(
                id = HOME_KIT,
                publication = MedKit.Publication.PUBLISHED,
                participantCount = 3
            ).toMedKitStorageEntity()
        )

        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.NEEDS_NETWORK, outcome)
        assertNotNull(database.medKits().find(HOME_KIT))
        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
    }

    /** Целевую аптечку удалили, пока человек выбирал: не записано ничего, и сказано почему. */
    @Test
    fun aTargetThatIsGoneChangesNothing() = runTest {
        val outcome = removal.remove(HOME_KIT, transferTo = Uuid.random())

        assertEquals(MedKitRemoval.Outcome.TARGET_GONE, outcome)
        assertNotNull(database.medKits().find(HOME_KIT))
        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
    }

    /** Переносить в саму себя нечего: содержимое и так там. */
    @Test
    fun aMedKitIsNotItsOwnTarget() = runTest {
        val outcome = removal.remove(HOME_KIT, transferTo = HOME_KIT)

        assertEquals(MedKitRemoval.Outcome.TARGET_IS_THE_SAME, outcome)
        assertNotNull(database.medKits().find(HOME_KIT))
    }

    @Test
    fun aMedKitThatIsAlreadyGoneSaysSo() = runTest {
        assertEquals(MedKitRemoval.Outcome.MED_KIT_GONE, removal.remove(Uuid.random()))
    }
}
