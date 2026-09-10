package com.kert0n.medapp.domain.calc.coverage

import com.kert0n.medapp.domain.model.intake.CourseIntake
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.FIRST_PLANNED_AT
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_ON
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.time.LocalTime
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Источники расходуются сверху вниз и сами не появляются (PLAN D5). */
class AssignSourcesTest {

    private val availability = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    /** Пять пунктов подряд, каждый со своим идентификатором и своим временем. */
    private val plan: List<CourseIntake> = (0 until 5).map { day ->
        plannedIntake(
            id = Uuid.parse("00000000-0000-4000-8000-00000000007$day"),
            scheduledOn = FIRST_SCHEDULED_ON.plusDays(day.toLong()),
            scheduledTime = LocalTime.of(9, 0),
            plannedAt = FIRST_PLANNED_AT.plusSeconds(86_400L * day)
        )
    }

    private fun assigned(vararg allocations: Pair<Uuid, Int>) = assignSources(
        course = activeCourse(sources = allocations.map { source(it.first, it.second) }),
        upcoming = plan,
        availability = availability
    ).let { found -> plan.map { found[it.id] } }

    @Test
    fun stackIsSpentTopDown() {
        // Два приёма из первой пачки, дальше вторая: допить начатую и перейти к следующей.
        assertEquals(
            listOf(PACK, PACK, OTHER_PACK, OTHER_PACK, OTHER_PACK),
            assigned(PACK to 2, OTHER_PACK to 3)
        )
    }

    @Test
    fun reorderingTheStackReordersTheSpending() {
        val swapped = activeCourse(sources = listOf(source(OTHER_PACK, 3), source(PACK, 2)))
            .let { assignSources(it, plan, availability) }
        assertEquals(
            listOf(OTHER_PACK, OTHER_PACK, OTHER_PACK, PACK, PACK),
            plan.map { swapped[it.id] }
        )
    }

    @Test
    fun intakeWithoutASuppliedSourceIsNotWrittenWithAFullDose() {
        // Выделено три дозы на пять приёмов: последние два получают явный признак
        // необеспеченности, а не «полную дозу неизвестно откуда».
        assertEquals(
            listOf(PACK, PACK, PACK, null, null),
            assigned(PACK to 3)
        )
    }

    @Test
    fun courseWithoutSourcesStillHasItems() {
        // План без источников порождает пункты, и все они необеспечены (PLAN H1).
        assertEquals(List(5) { null }, assigned())
    }

    @Test
    fun remainderSmallerThanADoseDoesNotSpillIntoTheNextSource() {
        // По одной таблетке в двух пачках при дозе в две: обеспеченных приёмов ноль, а не один.
        val singles = availability(PACK to tablets("1"), OTHER_PACK to tablets("1"))
        val found = assignSources(
            course = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 5))),
            upcoming = plan,
            availability = singles
        )
        assertEquals(List(5) { null }, plan.map { found[it.id] })
    }

    @Test
    fun packageGivesNoMoreThanItPhysicallyHas() {
        // Выделено пять доз, а свободно четыре таблетки — две дозы: дальше идёт вторая пачка.
        val shrunk = availability(PACK to tablets("4"), OTHER_PACK to tablets("12"))
        val found = assignSources(
            course = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 5))),
            upcoming = plan,
            availability = shrunk
        )
        assertEquals(listOf(PACK, PACK, OTHER_PACK, OTHER_PACK, OTHER_PACK), plan.map { found[it.id] })
    }

    @Test
    fun unknownAvailabilityIsNotSpentEither() {
        // Исход операции по первой пачке не установлен: до сверки она не выдаётся за источник,
        // и расход идёт со второй.
        val found = assignSources(
            course = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 2))),
            upcoming = plan,
            availability = availability(OTHER_PACK to tablets("12"))
        )
        assertEquals(listOf(OTHER_PACK, OTHER_PACK, null, null, null), plan.map { found[it.id] })
    }

    @Test
    fun similarPackagesAreNotSubstituted() {
        // Третья пачка того же лекарства лежит рядом и доступна, но в стек не встаёт сама.
        val elsewhere = Uuid.parse("00000000-0000-4000-8000-000000000023")
        val found = assignSources(
            course = activeCourse(sources = listOf(source(PACK, 2))),
            upcoming = plan,
            availability = availability(PACK to tablets("20"), elsewhere to tablets("50"))
        )
        assertEquals(listOf(PACK, PACK, null, null, null), plan.map { found[it.id] })
    }

    @Test
    fun onlyUnansweredItemsOfThisCourseAreLaidOut() {
        val answered = plannedIntake().confirm(PACK, HOME_KIT, tablets("2"), LATER)
        val course = activeCourse(sources = listOf(source(PACK, 5)))
        assertThrows(IllegalArgumentException::class.java) {
            assignSources(course, listOf(answered), availability)
        }
        val alien = plannedIntake(courseId = Uuid.parse("00000000-0000-4000-8000-000000000052"))
        assertThrows(IllegalArgumentException::class.java) {
            assignSources(course, listOf(alien), availability)
        }
        assertEquals(COURSE, course.id)
    }
}
