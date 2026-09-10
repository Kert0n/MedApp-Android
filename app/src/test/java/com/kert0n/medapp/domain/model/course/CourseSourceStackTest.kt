package com.kert0n.medapp.domain.model.course

import com.kert0n.medapp.domain.model.pack.PackageAccess
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.value.Quantity
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Источник — значение со своим местом в стеке: тождество даёт пара (курс, пачка), приоритет —
 * позиция (PLAN D5).
 */
class CourseSourceStackTest {

    private val home = pack(id = PACK, formId = TABLET_FORM, quantity = tablets("20"))
    private val dacha = pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = tablets("12"))

    private fun draftWithDose() = course(doseAmount = BigDecimal("2"))

    @Test
    fun attachedSourceGoesLastInTheStack() {
        // Порядок — приоритет расходования, и новая пачка встаёт после уже подключённых:
        // допить начатую и перейти к следующей — обычное намерение.
        val withTwo = draftWithDose()
            .attach(home, doses = doses(5), at = LATER).getOrThrow()
            .attach(dacha, doses = doses(4), at = LATER).getOrThrow()
        assertEquals(listOf(PACK, OTHER_PACK), withTwo.sources.map { it.packageId })
        assertEquals(listOf(doses(5), doses(4)), withTwo.sources.map { it.allocatedDoses })
        assertEquals(doses(9), withTwo.allocatedDosesTotal)
    }

    @Test
    fun samePackageDoesNotEnterTheStackTwice() {
        val once = draftWithDose().attach(home, doses = doses(5), at = LATER).getOrThrow()
        val again = once.attach(home, doses = doses(1), at = LATER)
        assertEquals(CourseRejection.ALREADY_ATTACHED, again.rejection())
    }

    @Test
    fun unusablePackageIsNotASource() {
        val archived = pack(formId = TABLET_FORM, lifecycle = PackageLifecycle.ARCHIVED)
        val lost = pack(id = OTHER_PACK, formId = TABLET_FORM, access = PackageAccess.LOST)
        assertEquals(
            CourseRejection.PACKAGE_UNUSABLE,
            draftWithDose().attach(archived, doses = doses(1), at = LATER).rejection()
        )
        assertEquals(
            CourseRejection.PACKAGE_UNUSABLE,
            draftWithDose().attach(lost, doses = doses(1), at = LATER).rejection()
        )
    }

    @Test
    fun reorderMovesPriority() {
        val stack = draftWithDose()
            .attach(home, doses = doses(5), at = LATER).getOrThrow()
            .attach(dacha, doses = doses(4), at = LATER).getOrThrow()
        val swapped = stack.reorder(from = 1, to = 0, at = LATER)
        assertEquals(listOf(OTHER_PACK, PACK), swapped.sources.map { it.packageId })
        assertEquals(listOf(doses(4), doses(5)), swapped.sources.map { it.allocatedDoses })
    }

    @Test
    fun reorderOutsideTheStackIsAProgrammerError() {
        val stack = draftWithDose().attach(home, doses = doses(5), at = LATER).getOrThrow()
        assertThrows(IllegalArgumentException::class.java) { stack.reorder(0, 1, LATER) }
    }

    @Test
    fun allocationOfASourceIsTheReservationInPackageUnits() {
        // Целевой объём серверной брони = выделение × доза (PLAN D5).
        val stack = draftWithDose().attach(home, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(Quantity(BigDecimal("10"), TABLETS), stack.allocatedOf(PACK))
        assertNull(stack.allocatedOf(OTHER_PACK))
    }

    @Test
    fun allocationIsUnknownWhileTheDoseIs() {
        // Выдумывать количество из незаданной дозы нельзя: «пачка выбрана, доза ещё нет» —
        // законное состояние черновика.
        val stack = course().attach(home, doses = doses(5), at = LATER).getOrThrow()
        assertNull(stack.allocatedOf(PACK))
        assertEquals(doses(5), stack.allocatedDosesTotal)
    }

    @Test
    fun changingSourcesAgesTheRevision() {
        val attached = draftWithDose().attach(home, doses = doses(5), at = LATER)
        assertEquals(1L, attached.getOrThrow().revision)
        assertEquals(2L, attached.getOrThrow().detach(PACK, LATER).revision)
    }

    @Test
    fun sourcesOfAnActiveCourseAreStillEditable() {
        // Это не изменение назначенной дозы или календаря, поэтому менять можно (PLAN D5).
        val active = activeCourse(sources = listOf(source(PACK, 5)))
        val widened = active.attach(dacha, doses = doses(4), at = LATER).getOrThrow()
        assertEquals(doses(9), widened.allocatedDosesTotal)
        assertEquals(schedule(), widened.schedule)
        assertEquals(BigDecimal("2"), widened.doseAmount)
    }

    @Test
    fun closedCourseKeepsItsSourcesAsHistory() {
        val cancelled = activeCourse(sources = listOf(source(PACK, 5))).cancel(LATER)
        assertEquals(CourseRejection.COURSE_CLOSED, cancelled.attach(dacha, doses(1), LATER).rejection())
        assertThrows(IllegalStateException::class.java) { cancelled.detach(PACK, LATER) }
        assertEquals(listOf(PACK), cancelled.sources.map { it.packageId })
    }

    @Test
    fun completionAndCancellationReleaseWhatIsLeftAllocated() {
        // Оставшегося выделения у закрытого курса нет: это то же событие, что снятие брони.
        val active = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)))
        assertEquals(doses(0), active.complete(LATER).allocatedDosesTotal)
        assertEquals(doses(0), active.cancel(LATER).allocatedDosesTotal)
        assertEquals(2, active.cancel(LATER).sources.size)
    }

    @Test
    fun activationRequiresScheduleDoseAndSource() {
        val bare = course()
        assertEquals(CourseRejection.SCHEDULE_MISSING, bare.activate(LATER).rejection())
        val scheduled = bare.setDraftSchedule(schedule(), LATER)
        assertEquals(CourseRejection.DOSE_MISSING, scheduled.activate(LATER).rejection())
        val dosed = scheduled.setDraftDose(BigDecimal("2"), LATER)
        // Единицы всё ещё нет — её фиксирует первый источник, поэтому доза не собралась.
        assertEquals(CourseRejection.DOSE_MISSING, dosed.activate(LATER).rejection())
        val sourced = dosed.attach(home, doses = doses(5), at = LATER).getOrThrow()
        val active = sourced.activate(LATER).getOrThrow()
        assertEquals(CourseStatus.ACTIVE, active.status)
        assertEquals(CourseRejection.NOT_DRAFT, active.activate(LATER).rejection())
    }

    @Test
    fun draftWithSourcesButNoScheduleIsRejectedForActivationNotForSaving() {
        // Черновик с выбранными пачками сохраняется: броней у него нет, упаковку он не занимает.
        val chosen = draftWithDose().attach(home, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(CourseStatus.DRAFT, chosen.status)
        assertEquals(CourseRejection.SCHEDULE_MISSING, chosen.activate(LATER).rejection())
    }

    @Test
    fun activationDoesNotAgeTheRevision() {
        // Активация не меняет ни расписания, ни источников: материализованным пунктам нечего
        // объявлять устаревшими.
        val ready = draftWithDose()
            .setDraftSchedule(schedule(), LATER)
            .attach(home, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(ready.revision, ready.activate(LATER).getOrThrow().revision)
    }

    @Test
    fun twoSourcesWithTheSamePackageAreNotRepresentable() {
        val duplicated = runCatching {
            course(
                doseAmount = BigDecimal("2"),
                unitId = TABLETS,
                formId = TABLET_FORM,
                sources = listOf(source(PACK, 1), source(PACK, 2))
            )
        }
        assertTrue(duplicated.exceptionOrNull() is IllegalArgumentException)
    }

    private fun Result<Course>.rejection(): CourseRejection? =
        (exceptionOrNull() as? CourseRejected)?.reason
}
