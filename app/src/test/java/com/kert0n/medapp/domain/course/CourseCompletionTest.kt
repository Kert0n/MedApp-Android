package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Последняя доза заканчивает лечение — политика, а не часть подтверждения приёма: её зовут все,
 * кто двигает прогресс, и проверяется она отдельно (PLAN D5).
 */
class CourseCompletionTest {

    private val week = activeCourse(totalDoses = 7)

    @Test
    fun theLastDoseReachesTheEnd() {
        assertTrue(CourseCompletion(week, week.progress(taken = 7)).reached)
        assertFalse(CourseCompletion(week, week.progress(taken = 6)).reached)
        assertFalse(CourseCompletion(week, week.progress(taken = 6, missed = 1)).reached)
    }

    @Test
    fun aDoseTakenOffPlanCountsTowardsTheEnd() {
        val corrected = week.setTakenOffPlan(com.kert0n.medapp.domain.value.Doses(2), com.kert0n.medapp.fixture.availability(), LATER)
        assertTrue(CourseCompletion(corrected, corrected.progress(taken = 5)).reached)
    }

    @Test
    fun closingCompletesTheRecordAndCancelsOnlyThePlanned() {
        val taken = plannedIntake().confirm(com.kert0n.medapp.fixture.pack().take(com.kert0n.medapp.fixture.dose("2"), LATER).getOrThrow())
        val planned = plannedIntake(id = OTHER_INTAKE)

        val closing = CourseCompletion(week, week.progress(taken = 7)).close(courseRecord(), listOf(taken, planned), LATER)

        assertEquals(CourseRecord.Outcome.COMPLETED, closing.record.outcome)
        assertEquals(LATER, closing.record.closedAt)
        assertEquals(listOf(OTHER_INTAKE), closing.cancelled.map { it.id })
        assertEquals(IntakeStatus.CANCELLED, closing.cancelled.single().status)
    }

    @Test
    fun anUnfinishedCourseCannotBeClosedAsCompleted() {
        assertThrows(IllegalStateException::class.java) {
            CourseCompletion(week, week.progress(taken = 3)).close(courseRecord(), emptyList(), LATER)
        }
    }
}
