package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Курс начинается заметкой: черновик с одним названием — законное сохранённое состояние, а не
 * полуфабрикат (PLAN D5).
 */
class CourseTest {

    @Test
    fun draftWithNothingButATitleIsALegitimateCourse() {
        val draft = course()
        assertEquals(CourseStatus.DRAFT, draft.status)
        assertNull(draft.note)
        assertNull(draft.doseAmount)
        assertNull(draft.dose)
    }

    @Test
    fun doseIsAValueOnlyWhenTheUnitIsKnownToo() {
        // Единицу фиксирует первый источник, дозу задаёт человек, и порядок бывает любым.
        assertNull(course(doseAmount = BigDecimal("2")).dose)
        assertNull(course(unitId = TABLETS).dose)
        assertEquals(
            Quantity(BigDecimal("2"), TABLETS),
            course(doseAmount = BigDecimal("2"), unitId = TABLETS).dose
        )
    }

    @Test
    fun renamedCourseIsTheSameCourse() {
        val original = course(title = "Парацетомол")
        val fixed = original.rename("Парацетамол", note = "по рецепту", at = LATER)
        assertEquals(original, fixed)
        assertEquals(original.hashCode(), fixed.hashCode())
        assertEquals("Парацетамол", fixed.title)
        assertEquals("по рецепту", fixed.note)
        assertEquals(LATER, fixed.updatedAt)
    }

    @Test
    fun renamingDoesNotAgeTheRevision() {
        // Редакция связывает курс с уже материализованными приёмами: исправленная опечатка не
        // должна объявлять их устаревшими.
        val renamed = course(revision = 3).rename("Другое название", note = null, at = LATER)
        assertEquals(Revision(3), renamed.revision)
    }

    @Test
    fun activeCourseStillGetsRenamed() {
        // Название и заметка — не назначенное лечение (PLAN D5).
        val active = activeCourse().rename("Курс", null, LATER)
        assertEquals("Курс от врача", active.rename("Курс от врача", null, LATER).title)
    }

    @Test
    fun settingTheDraftDoseRaisesTheRevision() {
        val dosed = course().setDose(BigDecimal("2"), at = LATER)
        assertEquals(BigDecimal("2"), dosed.doseAmount)
        assertEquals(Revision(1), dosed.revision)
        assertEquals(LATER, dosed.updatedAt)
    }

    @Test
    fun settingTheDraftScheduleRaisesTheRevision() {
        // Расписание меняет состав будущих пунктов, поэтому редакция растёт — в отличие от
        // переименования.
        val planned = course().setSchedule(schedule(), at = LATER)
        assertEquals(schedule(), planned.schedule)
        assertEquals(Revision(1), planned.revision)
    }

    @Test
    fun activationCarriesTheDoseAndScheduleOverUnchanged() {
        // Менять их после активации нечем: переходов `setDose` и `setSchedule` у назначенного
        // курса нет вовсе. Изменившееся лечение — отмена прежнего курса и новый (PLAN D5).
        val draft = course(doseAmount = BigDecimal("2"), schedule = schedule())
            .attach(pack(formId = TABLET_FORM), doses(1), LATER).getOrThrow()
        val planned = draft.activate(LATER).getOrThrow()
        assertEquals(tablets("2"), planned.dose)
        assertEquals(schedule(), planned.schedule)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDoseIsNotTreatment() {
        course(doseAmount = BigDecimal.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDoseIsRejected() {
        course(doseAmount = BigDecimal("-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTitleIsRejected() {
        course(title = "   ")
    }

    @Test
    fun titleAndNoteFillingTheirLimitsFit() {
        val long = course(
            title = "я".repeat(COURSE_TITLE_MAX_LENGTH),
            note = "я".repeat(COURSE_NOTE_MAX_LENGTH)
        )
        assertEquals(COURSE, long.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noteOverTheLimitIsRejected() {
        course(note = "я".repeat(COURSE_NOTE_MAX_LENGTH + 1))
    }
}
