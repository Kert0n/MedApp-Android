package com.kert0n.medapp.domain.model.course

import com.kert0n.medapp.domain.model.intake.IntakeStatus
import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.LocalTime
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Изменившееся лечение — это **отмена прежнего курса с сохранением истории и новый курс**, а не
 * правка действующего (PLAN C1, D5).
 *
 * Сценарий проверяется целиком, потому что по частям он выглядит безобидно: каждый отдельный
 * отказ понятен, а вместе они и есть то самое решение — прошлые приёмы не должны оказаться
 * записанными в дозе, которой у курса больше нет.
 */
class CourseReplacementTest {

    private val newCourseId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000053")

    private val old = activeCourse(sources = listOf(source(PACK, 5)))

    private val takenYesterday = plannedIntake(courseRevision = old.revision)
        .confirm(PACK, HOME_KIT, tablets("2"), EARLIER)

    private val plannedTomorrow = plannedIntake(
        id = Uuid.parse("00000000-0000-4000-8000-000000000071"),
        courseRevision = old.revision
    )

    @Test
    fun activeDoseAndScheduleAreNotEditable() {
        assertThrows(IllegalStateException::class.java) { old.setDraftDose(BigDecimal("3"), LATER) }
        assertThrows(IllegalStateException::class.java) {
            old.setDraftSchedule(schedule(times = listOf(LocalTime.of(21, 0))), LATER)
        }
    }

    @Test
    fun replacementIsAnotherCourseAndTheOldOneStaysAsHistory() {
        val cancelled = old.cancel(LATER)
        val replacement = course(
            id = newCourseId,
            title = old.title,
            doseAmount = BigDecimal("3"),
            createdAt = LATER,
            updatedAt = LATER
        )
            .setDraftSchedule(schedule(times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))), LATER)
            .attach(pack(id = PACK, formId = TABLET_FORM, quantity = tablets("20")), doses = doses(5), at = LATER)
            .getOrThrow()
            .activate(LATER)
            .getOrThrow()

        // Другой курс, а не тот же самый: тождество — id.
        assertNotEquals(old, replacement)
        assertEquals(CourseStatus.ACTIVE, replacement.status)

        // История прежнего курса на месте: расписание, доза и стек источников остались как были.
        assertEquals(CourseStatus.CANCELLED, cancelled.status)
        assertEquals(schedule(), cancelled.schedule)
        assertEquals(BigDecimal("2"), cancelled.doseAmount)
        assertEquals(TABLETS, cancelled.unitId)
        assertEquals(listOf(PACK), cancelled.sources.map { it.packageId })
        // Выделение освобождено — броней у отменённого курса нет.
        assertEquals(doses(0), cancelled.allocatedDosesTotal)
        assertEquals(EARLIER, cancelled.createdAt)
    }

    @Test
    fun cancellationDoesNotRewriteWhatAlreadyHappened() {
        // Отмена не переписывает состоявшиеся приёмы, их времена и количества (PLAN D5).
        assertEquals(IntakeStatus.TAKEN, takenYesterday.status)
        assertEquals(tablets("2"), takenYesterday.takenAmount)
        assertEquals(old.id, takenYesterday.courseId)
        assertEquals(old.revision, takenYesterday.courseRevision)

        // А будущий неотвеченный пункт отменяется вместе с курсом.
        val cancelledItem = plannedTomorrow.cancel(LATER)
        assertEquals(IntakeStatus.CANCELLED, cancelledItem.status)
        assertEquals(tablets("2"), cancelledItem.plannedAmount)
        assertThrows(IllegalStateException::class.java) {
            takenYesterday.cancel(LATER)
        }
    }

    @Test
    fun cancelledCourseIsNotReopened() {
        val cancelled = old.cancel(LATER)
        assertThrows(IllegalStateException::class.java) { cancelled.cancel(LATER) }
        assertThrows(IllegalStateException::class.java) { cancelled.complete(LATER) }
        assertEquals(
            CourseRejection.NOT_DRAFT,
            (cancelled.activate(LATER).exceptionOrNull() as CourseRejected).reason
        )
    }
}
