package com.kert0n.medapp.feature.packages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.PackagePending
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
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
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.intake.toStorageEntity as toIntakeStorageEntity
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Человек выбросил коробку (ТЗ 4.1.1.3, PLAN D3). Коробки нет, история есть, курс её потерял
 * своим переходом — а не каскадом схемы. Общая коробка уходит командой и до ответа сервера
 * остаётся строкой с нулём в проекции (E1).
 */
@RunWith(AndroidJUnit4::class)
class PackageRemovalTest {

    private lateinit var database: MedAppDatabase
    private lateinit var removal: PackageRemoval

    private val movementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        removal = Scenarios(database, LATER).packageRemoval
        database.packageRepository().add(pack(id = PACK, quantity = tablets("20"), form = TABLET_FORM))
        database.stockMovements().insert(
            StockMovement.Receipt(movementId, pack(id = PACK).ref, tablets("20"), Instant.EPOCH, LATER)
                .toMovementStorageEntity()
        )
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        database.courseRepository().activate(CourseDraft.Activation(plan, courseRecord(prescription = plan.prescription)))
        database.intakes().upsert(
            plannedIntake().confirm(pack(id = PACK).take(dose("2"), LATER).getOrThrow()).toIntakeStorageEntity()
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun commands(): List<SyncCommand> = database.syncOperations().all()
        .map { (it.toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation.command }

    @Test
    fun aLocalPackageIsGoneAndItsHistoryAndCourseSurvive() = runTest {
        val outcome = removal.remove(PACK)

        assertEquals(PackageRemoval.Outcome.REMOVED, outcome)
        assertNull(database.packageRepository().find(PACK))
        val plan = requireNotNull(database.courseRepository().findPlan(COURSE))
        assertTrue(plan.sources.isEmpty())
        assertEquals(Revision(2), plan.revision)
        assertNull(database.courses().courseHolding(PACK))
        // Выброшенное объясняет себя: к приходу добавляется утилизация всего остатка. Без неё
        // двадцать таблеток исчезли бы из учёта никем не принятыми, и «истрачено» не сошлось (H6).
        val history = database.stockMovements().ofPackage(PACK).map { it.toDomain(VOCABULARY) }
        assertEquals(2, history.size)
        assertEquals(tablets("20"), history.filterIsInstance<StockMovement.Disposal>().single().amount)
        val intake = requireNotNull(database.intakes().find(INTAKE)).toDomain(VOCABULARY)
        assertEquals("Парацетамол", intake.taken?.pkg?.name)
        assertEquals(emptyList<SyncCommand>(), commands())
    }

    /**
     * Общая коробка: решение и подтверждение — разные моменты (PLAN E1, E6). Серверу уходит
     * `Delete` со своим предусловием, а коробка до ответа **цела**: сосед мог отложить её себе, и
     * выбросить её молча нельзя. Лечение держит её источником, следа ещё нет, а проекция уже
     * показывает ноль — человеку видно, что коробка помечена.
     */
    @Test
    fun aSharedPackageIsMarkedAndWaitsForTheShelfToAgree() = runTest {
        database.medKits().upsert(
            medKit(id = HOME_KIT, publication = MedKit.Publication.PUBLISHED, participantCount = 2).toMedKitStorageEntity()
        )

        val outcome = removal.remove(PACK)

        assertEquals(PackageRemoval.Outcome.MARKED, outcome)
        assertNotNull(database.packageRepository().find(PACK))
        assertEquals(
            listOf(PACK),
            requireNotNull(database.courseRepository().findPlan(COURSE)).sources.map { it.pkg.id }
        )
        assertEquals(1, database.stockMovements().ofPackage(PACK).size)
        assertEquals(listOf(PackageSyncCommand.Delete(PACK)), commands())
        val projection = requireNotNull(database.packageRepository().observe(PACK).first())
        assertEquals(tablets("0"), projection.availability.effective)
        assertTrue(projection.hasUnconfirmedChanges)
        // Коробка показана — и показана помеченной: человеку видно, что решение принято, а полка
        // ещё не согласилась (PLAN E1).
        assertEquals(PackagePending.REMOVAL, projection.pending)
        assertEquals(PackagePending.REMOVAL, database.packageRepository().pendingOf(PACK))
    }

    @Test
    fun aPackageThatIsAlreadyGoneSaysSo() = runTest {
        assertEquals(PackageRemoval.Outcome.GONE, removal.remove(Uuid.random()))
    }
}
