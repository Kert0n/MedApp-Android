package com.kert0n.medapp.storage.intake

import android.database.sqlite.SQLiteConstraintException
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.FIRST_PLANNED_AT
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.rejectedByDatabase
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.unplannedIntake
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.storage.course.toStorageEntity as toRecordStorageEntity
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity as toPackageStorageEntity
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.TABLETS_ID
import com.kert0n.medapp.fixture.medKit

/**
 * История не удаляется вместе с упаковкой: приёмы и движения держат её ключами `RESTRICT`,
 * а архивирование пачки их не касается вовсе (PLAN D7, F1).
 */
class HistoryDaoTest {

    private lateinit var database: MedAppDatabase
    private val intakes get() = database.intakes()
    private val movements get() = database.stockMovements()

    private val movementId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000081")

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        for (id in listOf(PACK, OTHER_PACK)) {
            val pkg = pack(id = id)
            database.packages().save(pkg.toPackageStorageEntity(), pkg.toDetailsStorageEntity())
        }
        database.courses().upsertRecord(courseRecord().toRecordStorageEntity())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun plannedIntakeAndItsConfirmationComeBackWhole() = runTest {
        intakes.upsert(plannedIntake().toStorageEntity())
        val stored = requireNotNull(intakes.find(INTAKE)).toDomain(VOCABULARY)
        assertEquals(IntakeStatus.PLANNED, stored.status)

        val taken = plannedIntake().confirm(pack().take(dose("2"), LATER).getOrThrow())
        intakes.upsert(taken.toStorageEntity())
        assertEquals(taken.taken, requireNotNull(intakes.find(INTAKE)).toDomain(VOCABULARY).taken)
    }

    /** Один пункт расписания заводится один раз: повторная материализация идемпотентна. */
    @Test
    fun sameSlotIsNotMaterialisedTwice() = runTest {
        val slot = plannedIntake().toStorageEntity()
        intakes.insertPlannedIfMissing(listOf(slot))
        val again = plannedIntake(id = OTHER_INTAKE).toStorageEntity()

        val inserted = intakes.insertPlannedIfMissing(listOf(again))

        assertEquals(listOf(-1L), inserted)
        assertNotNull(intakes.find(INTAKE))
        assertEquals(null, intakes.find(OTHER_INTAKE))
    }

    /** Ответ идёт условным `UPDATE`: повтор уже совершённого ничего не меняет второй раз. */
    @Test
    fun answeringTwiceChangesNothingTheSecondTime() = runTest {
        intakes.upsert(plannedIntake().toStorageEntity())

        val first = intakes.answerIfStatusIs(
            id = INTAKE,
            from = listOf(IntakeStatus.PLANNED, IntakeStatus.MISSED),
            to = IntakeStatus.TAKEN,
            at = LATER,
            packageId = PACK,
            amount = "2",
            unitId = TABLETS_ID,
            accounting = IntakeAccounting.LOCAL_APPLIED,
            operationId = null
        )
        val second = intakes.answerIfStatusIs(
            id = INTAKE,
            from = listOf(IntakeStatus.PLANNED),
            to = IntakeStatus.MISSED,
            at = LATER.plusSeconds(60),
            packageId = null,
            amount = null,
            unitId = TABLETS_ID,
            accounting = IntakeAccounting.NOT_APPLICABLE,
            operationId = null
        )

        assertEquals(1, first)
        assertEquals(0, second)
        val stored = requireNotNull(intakes.findEntity(INTAKE))
        assertEquals(IntakeStatus.TAKEN, stored.status)
        assertEquals(IntakeAccounting.LOCAL_APPLIED, stored.accounting)
    }

    @Test
    fun packageWithAnIntakeCannotBeDeleted() = runTest {
        intakes.upsert(plannedIntake().toStorageEntity())
        val refusal = rejectedByDatabase { database.packages().delete(PACK) }
        assertTrue("$refusal", refusal is SQLiteConstraintException)
    }

    @Test
    fun packageWithAMovementCannotBeDeleted() = runTest {
        movements.insert(
            StockMovement.Receipt(movementId, pack(), tablets("20"), medKit(), Instant.EPOCH, LATER)
                .toMovementStorageEntity()
        )
        val refusal = rejectedByDatabase { database.packages().delete(PACK) }
        assertTrue("$refusal", refusal is SQLiteConstraintException)
    }

    /** Архивирование — не удаление: приёмы, движения и внеплановые факты остаются на месте. */
    @Test
    fun archivingKeepsIntakesAndMovements() = runTest {
        intakes.upsert(plannedIntake().confirm(pack().take(dose("2"), LATER).getOrThrow()).toStorageEntity())
        intakes.upsert(unplannedIntake(id = OTHER_INTAKE).toStorageEntity())
        movements.insert(
            StockMovement.Receipt(movementId, pack(), tablets("20"), medKit(), Instant.EPOCH, LATER)
                .toMovementStorageEntity()
        )

        val archived = pack().consume(dose("20"))
        database.packages().upsertServerPart(archived.toPackageStorageEntity())

        assertNotNull(intakes.find(INTAKE))
        assertNotNull(intakes.find(OTHER_INTAKE))
        assertEquals(1, movements.ofPackage(PACK).size)
        assertEquals(
            Package.Lifecycle.ARCHIVED,
            requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY).lifecycle
        )
    }

    /**
     * Запись эпизода тоже под `RESTRICT`. Удаления записей у приложения нет вовсе, поэтому
     * попытка идёт прямым SQL: ограничение и стоит ради ошибки, которой в коде ещё нет.
     */
    @Test
    fun recordWithHistoryCannotBeDeleted() = runTest {
        intakes.upsert(plannedIntake().toStorageEntity())
        val refusal = rejectedByDatabase {
            database.openHelper.writableDatabase
                .execSQL("DELETE FROM course_records WHERE id = '$COURSE'")
        }
        assertTrue("$refusal", refusal is SQLiteConstraintException)
    }

    @Test
    fun movementsOfAPackageComeBackInTimeOrder() = runTest {
        val first = StockMovement.Receipt(movementId, pack(), tablets("20"), medKit(), Instant.EPOCH, Instant.EPOCH)
        val second = StockMovement.Recount(
            Uuid.parse("00000000-0000-4000-8000-000000000082"),
            pack(), tablets("20"), tablets("18"), medKit(), FIRST_PLANNED_AT, FIRST_PLANNED_AT
        )
        movements.insert(second.toMovementStorageEntity())
        movements.insert(first.toMovementStorageEntity())

        assertEquals(listOf(first, second), movements.ofPackage(PACK).map { it.toDomain(VOCABULARY) })
    }
}
