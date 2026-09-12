package com.kert0n.medapp.feature.medkits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.Scenarios
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.courseRepository
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
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
 * Человек убирает полку (ТЗ 4.1.1.2.3) — по коробкам, через домен, одной транзакцией. На
 * настоящей базе: то, ради чего всё это затевалось, — схема даёт убрать аптечку, а история
 * лечения это переживает (PLAN E6, D3, D6). Шесть случаев — по границе публикации источника и
 * цели: серверу об общей полке говорит одна команда аптечки, о местной коробке на общей полке —
 * её публикация, а общее содержимое на местную полку не снимается без публикации цели.
 */
@RunWith(AndroidJUnit4::class)
class MedKitRemovalTest {

    private lateinit var database: MedAppDatabase
    private lateinit var removal: MedKitRemoval

    private val movementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        removal = Scenarios(database, LATER).medKitRemoval
        database.packageRepository().add(pack(id = PACK, quantity = tablets("20"), form = TABLET_FORM))
        database.packageRepository().add(pack(id = OTHER_PACK, quantity = tablets("1")))
        database.stockMovements().insert(
            StockMovement.Receipt(movementId, pack(id = PACK).ref, tablets("20"), Instant.EPOCH, LATER)
                .toMovementStorageEntity()
        )
        // Курс держит PACK источником: разбор полки решает и его судьбу.
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        database.courseRepository().activate(CourseDraft.Activation(plan, courseRecord(prescription = plan.prescription)))
        database.intakes().upsert(
            plannedIntake().confirm(pack(id = PACK).take(dose("2"), LATER).getOrThrow())
                .toIntakeStorageEntity()
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun publish(vararg kits: Uuid) {
        for (kit in kits) {
            database.medKits().upsert(
                medKit(id = kit, publication = MedKit.Publication.PUBLISHED, participantCount = 2).toMedKitStorageEntity()
            )
        }
    }

    private suspend fun commands(): List<SyncCommand> = database.syncOperations().all()
        .map { (it.toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation.command }

    private suspend fun sourcesOfCourse(): List<Uuid> = database.courses().sourcePackagesOf(COURSE)

    /** «Забрал аптечку домой», обе местные: содержимое переезжает целиком, курс коробку не теряет. */
    @Test
    fun takingALocalMedKitAwayIntoALocalOneMovesEverythingAndKeepsTheCourse() = runTest {
        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(SHARED_KIT, database.packageRepository().find(OTHER_PACK)?.medKit?.id)
        assertEquals(listOf(PACK), sourcesOfCourse())
        assertEquals(1, database.stockMovements().ofPackage(PACK).size)
        assertEquals(emptyList<SyncCommand>(), commands())
    }

    /**
     * «Выбросил вместе с лекарствами» (ТЗ 4.1.1.2.3.1): коробок не остаётся, курс теряет источник
     * своим переходом, а история — запись эпизода, приём и движения — переживает: она держится за
     * записи о коробках (PLAN D3, D6).
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
        assertEquals(emptyList<Uuid>(), sourcesOfCourse())
        // Выброшенное объясняет себя: к приходу добавляется утилизация всего остатка (PLAN H6).
        val history = database.stockMovements().ofPackage(PACK).map { it.toDomain(VOCABULARY) }
        assertEquals(2, history.size)
        assertEquals(tablets("20"), history.filterIsInstance<StockMovement.Disposal>().single().amount)

        assertNotNull(database.courses().findRecord(COURSE))
        val intake = requireNotNull(database.intakes().find(INTAKE)).toDomain(VOCABULARY)
        assertEquals(dose("2"), intake.taken?.amount)
        assertEquals("Парацетамол", intake.taken?.pkg?.name)
    }

    /**
     * Местные коробки на общую полку: каждая публикуется в целевой аптечке, а выделение курса
     * едет следом бронью — на сервере оно иначе не появилось бы (PLAN E6).
     */
    @Test
    fun takingALocalMedKitAwayIntoASharedOnePublishesEachPackage() = runTest {
        publish(SHARED_KIT)

        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(listOf(PACK), sourcesOfCourse())
        val queued = commands()
        val creates = queued.filterIsInstance<PackageSyncCommand.Create>()
        assertEquals(setOf(PACK, OTHER_PACK), creates.mapTo(HashSet()) { it.packageId })
        assertEquals(SHARED_KIT, creates.first().medKitId)
        val claim = queued.filterIsInstance<PackageSyncCommand.SetClaim>().single()
        assertEquals(PACK, claim.packageId)
        assertEquals(tablets("10"), claim.amount)
        assertEquals(3, queued.size)
    }

    /**
     * Общая полка на общую: сервер переставляет всё сам одной командой аптечки и решает судьбу
     * броней; локально коробки только меняют место, курс их не теряет.
     */
    @Test
    fun takingASharedMedKitAwayIntoASharedOneIsOneCommand() = runTest {
        publish(HOME_KIT, SHARED_KIT)

        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(SHARED_KIT, database.packageRepository().find(OTHER_PACK)?.medKit?.id)
        assertEquals(listOf(PACK), sourcesOfCourse())
        assertEquals(listOf(MedKitSyncCommand.Delete(HOME_KIT, transferTo = SHARED_KIT)), commands())
    }

    /** Общую полку выбросили: коробок и частей нет, история цела, серверу — одна команда. */
    @Test
    fun throwingASharedMedKitOutIsOneCommand() = runTest {
        publish(HOME_KIT)

        val outcome = removal.remove(HOME_KIT, transferTo = null)

        assertEquals(MedKitRemoval.Outcome.REMOVED, outcome)
        assertNull(database.medKits().find(HOME_KIT))
        assertNull(database.packageRepository().find(PACK))
        assertEquals(emptyList<Uuid>(), sourcesOfCourse())
        assertNotNull(database.courses().findRecord(COURSE))
        // Выброшенное объясняет себя: к приходу добавляется утилизация всего остатка (PLAN H6).
        val history = database.stockMovements().ofPackage(PACK).map { it.toDomain(VOCABULARY) }
        assertEquals(2, history.size)
        assertEquals(tablets("20"), history.filterIsInstance<StockMovement.Disposal>().single().amount)
        assertEquals(listOf(MedKitSyncCommand.Delete(HOME_KIT)), commands())
    }

    /** Общее содержимое на местную полку сервер не снимает: сначала публикация цели (PLAN E5, E6). */
    @Test
    fun aSharedMedKitIntoALocalTargetNeedsTheTargetPublished() = runTest {
        publish(HOME_KIT)

        val outcome = removal.remove(HOME_KIT, transferTo = SHARED_KIT)

        assertEquals(MedKitRemoval.Outcome.TARGET_NEEDS_PUBLICATION, outcome)
        assertNotNull(database.medKits().find(HOME_KIT))
        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(emptyList<SyncCommand>(), commands())
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
