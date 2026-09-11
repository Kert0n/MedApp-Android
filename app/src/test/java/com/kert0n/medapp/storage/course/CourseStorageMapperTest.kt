package com.kert0n.medapp.storage.course

import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.MOSCOW
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.prescription
import com.kert0n.medapp.fixture.schedule
import com.kert0n.medapp.fixture.source
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.dose

/**
 * Курс, его времена и его источники собираются обратно тем же самым, а черновик и живой план
 * различаются не колонкой-состоянием, а тем, где живёт имя лечения (PLAN D5, F1).
 */
class CourseStorageMapperTest {

    private fun rowOf(entity: CourseStorageEntity, times: List<LocalTime>, sources: List<CourseSourceStorageEntity> = emptyList()) =
        CourseStorageRow(
            course = entity,
            times = times.map { CourseTimeStorageEntity(entity.id, it) },
            sources = sources
        )

    @Test
    fun draftKeepsItsNameNoteAndUnfinishedPrescription() {
        val draft = course(note = "спросить у врача", dose = dose("2"), form = TABLET_FORM, totalDoses = 5)
        val row = rowOf(draft.toStorageEntity(), times = emptyList())

        assertTrue(row.isDraft)
        val restored = row.toDraft(VOCABULARY)
        assertEquals(draft.title, restored.title)
        assertEquals(draft.note, restored.note)
        assertEquals(draft.dose, restored.dose)
        assertEquals(draft.form, restored.form)
        assertEquals(draft.totalDoses, restored.totalDoses)
        assertNull(restored.schedule)
        assertEquals(draft.revision, restored.revision)
    }

    @Test
    fun draftWithoutDoseComesBackWithoutIt() {
        val restored = rowOf(course().toStorageEntity(), times = emptyList()).toDraft(VOCABULARY)
        assertNull(restored.dose)
        assertNull(restored.form)
        assertNull(restored.totalDoses)
        assertTrue(restored.medicine.isEmpty)
    }

    @Test
    fun activePlanCarriesNoNameAtAll() {
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        val stored = plan.toStorageEntity()
        assertNull(stored.title)
        assertNull(stored.note)
        assertFalse(rowOf(stored, plan.schedule.times).isDraft)
    }

    @Test
    fun everyPartOfThePlanSurvivesTheRoundTrip() {
        val weekdays = schedule(
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            times = listOf(LocalTime.of(8, 30), LocalTime.of(21, 0)),
            zone = MOSCOW
        )
        val plan = activeCourse(
            doseAmount = BigDecimal("1.5"),
            schedule = weekdays,
            sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)),
            revision = 3,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH.plusSeconds(600)
        )
        val restored = rowOf(
            plan.toStorageEntity(),
            weekdays.times,
            plan.medicine.toSourceStorageEntities(COURSE)
        ).toPlan(VOCABULARY)

        assertEquals(plan.dose, restored.dose)
        assertEquals(plan.schedule, restored.schedule)
        assertEquals(plan.sources, restored.sources)
        assertEquals(TABLET_FORM, restored.form)
        assertEquals(TABLETS, restored.unit)
        assertEquals(plan.revision, restored.revision)
        assertEquals(plan.createdAt, restored.createdAt)
        assertEquals(plan.updatedAt, restored.updatedAt)
    }

    /** Порядок источников — приоритет расходования, и он читается из позиции, а не из вставки. */
    @Test
    fun sourcesComeBackInTheOrderOfTheirPositions() {
        val plan = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)))
        val shuffled = plan.medicine.toSourceStorageEntities(COURSE).reversed()

        val restored = rowOf(plan.toStorageEntity(), plan.schedule.times, shuffled).toPlan(VOCABULARY)
        assertEquals(listOf(PACK, OTHER_PACK), restored.sources.map { it.packageId })
        assertEquals(plan.sources, restored.sources)
    }

    @Test
    fun timesComeBackSortedEvenWhenRowsArriveInAnyOrder() {
        val evening = schedule(times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        val plan = activeCourse(schedule = evening, sources = listOf(source(PACK, 1)))
        val restored = rowOf(
            plan.toStorageEntity(),
            listOf(LocalTime.of(21, 0), LocalTime.of(9, 0)),
            plan.medicine.toSourceStorageEntities(COURSE)
        ).toPlan(VOCABULARY)
        assertEquals(evening.times, restored.schedule.times)
    }

    @Test
    fun openRecordKeepsItsPrescriptionSnapshot() {
        val record = courseRecord(note = "от врача", prescription = prescription(BigDecimal("1.5")))
        val restored = CourseRecordStorageRow(
            record.toStorageEntity(),
            record.prescription.schedule.times.map { CourseTimeStorageEntity(COURSE, it) }
        ).toDomain(VOCABULARY)

        assertEquals(record.title, restored.title)
        assertEquals(record.note, restored.note)
        assertEquals(record.prescription, restored.prescription)
        assertEquals(record.startedAt, restored.startedAt)
        assertTrue(restored.isOpen)
    }

    @Test
    fun closedRecordKeepsOutcomeAndMoment() {
        val closed = courseRecord().close(CourseRecord.Outcome.CANCELLED, Instant.EPOCH.plusSeconds(3600))
        val restored = CourseRecordStorageRow(
            closed.toStorageEntity(),
            closed.prescription.schedule.times.map { CourseTimeStorageEntity(COURSE, it) }
        ).toDomain(VOCABULARY)

        assertEquals(CourseRecord.Outcome.CANCELLED, restored.outcome)
        assertEquals(closed.closedAt, restored.closedAt)
        assertFalse(restored.isOpen)
    }

    /** Назначение записи то же, что у плана: они рождаются вместе и разойтись не могут. */
    @Test
    fun planAndRecordOfOneEpisodeStorePrescriptionTheSameWay() {
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        val record = courseRecord(prescription = plan.prescription)
        val storedPlan = plan.toStorageEntity()
        val storedRecord = record.toStorageEntity()

        assertEquals(storedPlan.id, storedRecord.id)
        assertEquals(storedPlan.doseAmount, storedRecord.doseAmount)
        assertEquals(storedPlan.unitId, storedRecord.unitId)
        assertEquals(storedPlan.start, storedRecord.start)
        assertEquals(storedPlan.endInclusive, storedRecord.endInclusive)
        assertEquals(storedPlan.daysOfWeek, storedRecord.daysOfWeek)
        assertEquals(storedPlan.zone, storedRecord.zone)
    }
}
