package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.doses
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.beginning
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Дозы, принятые мимо плана, — поправка к счёту курса, а не приём: расход уже учтён там, где
 * произошёл, и второй раз не считается (PLAN D5).
 */
class CourseTakenOffPlanTest {

    private val availability = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private val twoPacks = activeCourse(sources = listOf(source(PACK, 3), source(OTHER_PACK, 4)))

    @Test
    fun offPlanDosesReduceTheNeedAndTheFirstSourceInSpendOrder() {
        // Две дозы ушли из кармана: нужно на две меньше, и первый источник отдал две из трёх
        // выделенных — как отдал бы плановым приёмам.
        val corrected = twoPacks.setTakenOffPlan(2.doses, availability, LATER)
        assertEquals(5.doses, corrected.remainingDoses(taken = 0.doses))
        assertEquals(listOf(1.doses, 4.doses), corrected.sources.map { it.allocatedDoses })
        assertEquals(twoPacks.revision.next(), corrected.revision)
        assertEquals(LATER, corrected.updatedAt)
    }

    @Test
    fun moreThanTheFirstSourceHoldsSpillsIntoTheNext() {
        val corrected = twoPacks.setTakenOffPlan(5.doses, availability, LATER)
        assertEquals(listOf(0.doses, 2.doses), corrected.sources.map { it.allocatedDoses })
        assertEquals(2.doses, corrected.remainingDoses(taken = 0.doses))
    }

    @Test
    fun withoutSourcesOnlyTheNeedChanges() {
        val bare = activeCourse().setTakenOffPlan(3.doses, availability, LATER)
        assertEquals(4.doses, bare.remainingDoses(taken = 0.doses))
        assertTrue(bare.sources.isEmpty())
        assertEquals(activeCourse().revision.next(), bare.revision)
    }

    @Test
    fun theTotalIsSetNotAddedAndRaisingItSpendsOnlyTheDifference() {
        val two = twoPacks.setTakenOffPlan(2.doses, availability, LATER)
        val three = two.setTakenOffPlan(3.doses, availability, LATER)
        assertEquals(3.doses, three.takenOffPlan)
        assertEquals(listOf(0.doses, 4.doses), three.sources.map { it.allocatedDoses })
    }

    @Test
    fun loweringTheNumberDoesNotGrowTheReservationBack() {
        // Снимать бронь решал человек, и вернуть её догадкой нельзя: меняется только потребность.
        val lowered = twoPacks.setTakenOffPlan(2.doses, availability, LATER)
            .setTakenOffPlan(1.doses, availability, LATER)
        assertEquals(6.doses, lowered.remainingDoses(taken = 0.doses))
        assertEquals(listOf(1.doses, 4.doses), lowered.sources.map { it.allocatedDoses })
    }

    @Test
    fun sameNumberIsNotAChange() {
        assertSame(twoPacks, twoPacks.setTakenOffPlan(0.doses, availability, LATER))
    }

    @Test
    fun offPlanDosesMoveTheExpectedEndCloser() {
        val week = activeCourse()
        val corrected = week.setTakenOffPlan(2.doses, availability, LATER)
        assertEquals(
            week.schedule.start.plusDays(4),
            corrected.expectedEnd(0.doses, week.schedule.beginning)?.localDate
        )
    }

    @Test
    fun aTakenDoseStillCannotBeBuiltWithoutAPack() {
        // Поправка к счёту курса — не факт о пачке, и факта без пачки по-прежнему не бывает:
        // у каждого способа собрать `TakenDose` пачка среди аргументов.
        val constructors = TakenDose::class.java.constructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(
            constructors.all { ctor ->
                ctor.parameterTypes.any { it == Package::class.java || it == Uuid::class.java }
            }
        )
    }
}
