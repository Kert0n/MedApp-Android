package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.doses
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.dose
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
        assertNull(draft.note)
        assertNull(draft.doseAmount)
        assertNull(draft.dose)
    }

    @Test
    fun doseIsAValueOnlyWhenTheUnitIsKnownToo() {
        // Единицу фиксирует первый источник, дозу задаёт человек, и порядок бывает любым.
        assertNull(course(doseAmount = BigDecimal("2")).dose)
        assertNull(course(unit = TABLETS).dose)
        assertEquals(
            Dose(Quantity(BigDecimal("2"), TABLETS)),
            course(doseAmount = BigDecimal("2"), unit = TABLETS).dose
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
    fun startedTreatmentStillGetsRenamed() {
        // Название и заметка — не назначенное лечение, и правятся они всегда (PLAN D5). Живёт имя
        // в записи эпизода: так называют лечение, а не расписание, и второго места для него нет.
        val record = courseRecord(title = "Курс")
        assertEquals("Курс от врача", record.rename("Курс от врача", null).title)
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
            .attach(pack(form = TABLET_FORM), 1.doses, LATER).getOrThrow()
        val started = draft.activate(LATER).getOrThrow()
        assertEquals(dose("2"), started.course.dose)
        assertEquals(schedule(), started.course.schedule)
        // Запись эпизода несёт то же назначение: расходиться им нечем — менять его нельзя.
        assertEquals(started.course.prescription, started.record.prescription)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDoseIsNotTreatment() {
        course(doseAmount = BigDecimal.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startedTreatmentWithAZeroDoseIsNotRepresentable() {
        // Проверка черновика закрывала один путь; правило живёт на самой дозе, поэтому прямая
        // сборка действующего курса — и восстановление сохранённого — тоже её соблюдают.
        activeCourse(doseAmount = BigDecimal.ZERO)
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
            title = "я".repeat(CourseRecord.TITLE_MAX_LENGTH),
            note = "я".repeat(CourseRecord.NOTE_MAX_LENGTH)
        )
        assertEquals(COURSE, long.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noteOverTheLimitIsRejected() {
        course(note = "я".repeat(CourseRecord.NOTE_MAX_LENGTH + 1))
    }
}
