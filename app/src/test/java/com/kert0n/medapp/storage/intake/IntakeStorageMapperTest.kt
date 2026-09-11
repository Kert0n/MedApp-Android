package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.unplannedIntake
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY

/**
 * Плановый пункт и внеплановый факт лежат в одной таблице и различаются наличием курса.
 * Учёт расхода едет в тех же колонках, но доменная модель его не носит (PLAN D6, F1).
 */
class IntakeStorageMapperTest {

    @Test
    fun plannedIntakeComesBackPlanned() {
        val planned = plannedIntake()
        val restored = planned.toStorageEntity().toDomain(VOCABULARY) as CourseIntake

        assertEquals(planned.id, restored.id)
        assertEquals(planned.courseId, restored.courseId)
        assertEquals(planned.courseRevision, restored.courseRevision)
        assertEquals(planned.slot, restored.slot)
        assertEquals(planned.plannedAmount, restored.plannedAmount)
        assertEquals(planned.plannedPackageId, restored.plannedPackageId)
        assertEquals(IntakeStatus.PLANNED, restored.status)
        assertNull(restored.answer)
    }

    @Test
    fun unsuppliedIntakeStaysUnsupplied() {
        val restored = plannedIntake(plannedPackageId = null).toStorageEntity().toDomain(VOCABULARY) as CourseIntake
        assertNull(restored.plannedPackageId)
        assertEquals(false, restored.isSupplied)
    }

    /** Пачка факта может отличаться от плановой, и аптечка пишется на момент события. */
    @Test
    fun confirmedIntakeKeepsWhereTheDoseCameFrom() {
        val taken = plannedIntake().confirm(
            pkg = pack(id = OTHER_PACK, medKitId = SHARED_KIT),
            amount = dose("1.5"),
            at = LATER
        )
        val restored = taken.toStorageEntity().toDomain(VOCABULARY) as CourseIntake

        assertEquals(IntakeStatus.TAKEN, restored.status)
        assertEquals(taken.taken, restored.taken)
        assertEquals(OTHER_PACK, restored.taken?.packageId)
        assertEquals(SHARED_KIT, restored.taken?.medKitId)
        assertEquals(PACK, restored.plannedPackageId)
    }

    @Test
    fun everyAnswerComesBackAsItself() {
        val missed = plannedIntake().miss(LATER)
        val cancelled = plannedIntake().cancel(LATER)

        for (answered in listOf(missed, cancelled)) {
            val restored = answered.toStorageEntity().toDomain(VOCABULARY) as CourseIntake
            assertEquals(answered.status, restored.status)
            assertEquals(answered.answer, restored.answer)
            assertNull(restored.taken)
        }
    }

    @Test
    fun unplannedFactHasNoCourseAndIsAlwaysTaken() {
        val unplanned = unplannedIntake(takenAmount = dose("1"))
        val stored = unplanned.toStorageEntity()
        assertNull(stored.courseId)
        assertNull(stored.plannedAmount)

        val restored = stored.toDomain(VOCABULARY)
        assertTrue(restored is UnplannedIntake)
        assertEquals(IntakeStatus.TAKEN, restored.status)
        assertEquals(unplanned.dose, (restored as UnplannedIntake).dose)
    }

    /** Учёт расхода — обвязка доставки: он едет в колонках и не приезжает обратно в домен. */
    @Test
    fun accountingTravelsBesideTheIntakeAndNotInsideIt() {
        val operation = Uuid.parse("00000000-0000-4000-8000-000000000071")
        val sync = IntakeSyncState(
            intakeId = INTAKE,
            accounting = IntakeAccounting.PENDING,
            operationId = operation
        )
        val stored = plannedIntake().confirm(pack(), dose("2"), LATER).toStorageEntity(sync)

        assertEquals(sync, stored.syncState())
        assertEquals(IntakeStatus.TAKEN, stored.toDomain(VOCABULARY).status)
    }

    @Test
    fun accountingOfAnotherIntakeIsRejected() {
        val alien = IntakeSyncState(intakeId = COURSE)
        val failure = runCatching { plannedIntake().toStorageEntity(alien) }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class, failure!!::class)
    }
}
