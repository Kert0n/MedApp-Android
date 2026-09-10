package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.FIRST_PLANNED_AT
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_ON
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_TIME
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.fixture.unplannedIntake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Приём и его состояния (PLAN D6). Учёта расхода здесь нет: он живёт в `IntakeSyncState` слоя
 * данных, потому что существует только из-за сервера.
 */
class IntakeTest {

    @Test
    fun confirmedIntakeIsTheSameIntake() {
        // Подтверждение не делает приём другим приёмом: это тот же пункт, у которого появился
        // ответ. Тождество — id.
        val planned = plannedIntake()
        val taken = planned.confirm(pack(), dose("2"), LATER)
        assertEquals(planned, taken)
        assertEquals(planned.hashCode(), taken.hashCode())
        assertEquals(IntakeStatus.TAKEN, taken.status)
        assertEquals(LATER, taken.taken?.at)
        assertEquals(LATER, taken.answer?.at)
    }

    @Test
    fun answerDoesNotRewriteThePlan() {
        // Пункт порождён редакцией расписания, и ответ не переписывает ни назначенное время,
        // ни плановую дозу, ни плановую пачку.
        val fromDacha = pack(id = OTHER_PACK, medKitId = SHARED_KIT)
        val taken = plannedIntake().confirm(fromDacha, dose("1"), LATER)
        assertEquals(FIRST_PLANNED_AT, taken.plannedAt)
        assertEquals(FIRST_SCHEDULED_ON, taken.slot.localDate)
        assertEquals(FIRST_SCHEDULED_TIME, taken.slot.localTime)
        assertEquals(dose("2"), taken.plannedAmount)
        assertEquals(PACK, taken.plannedPackageId)
        // А фактические пачка, аптечка и количество — те, что назвал человек.
        assertEquals(OTHER_PACK, taken.taken?.packageId)
        assertEquals(SHARED_KIT, taken.taken?.medKitId)
        assertEquals(dose("1"), taken.taken?.amount)
    }

    @Test
    fun factualAmountMayDifferFromThePlanned() {
        val taken = plannedIntake().confirm(pack(), dose("3"), LATER)
        assertEquals(dose("3"), taken.taken?.amount)
        assertEquals(dose("2"), taken.plannedAmount)
    }

    @Test
    fun missedItemIsStillConfirmable() {
        // Поздний ответ проверяет текущий источник и остаток заново, но пункт остаётся тем же.
        val missed = plannedIntake().miss(LATER)
        assertEquals(IntakeStatus.MISSED, missed.status)
        val late = missed.confirm(pack(), dose("2"), LATER.plusSeconds(3600))
        assertEquals(IntakeStatus.TAKEN, late.status)
    }

    @Test
    fun confirmedIntakeIsNotConfirmedTwice() {
        // Второе подтверждение — второй факт со своим идентификатором, а не тот же самый.
        val taken = plannedIntake().confirm(pack(), dose("2"), LATER)
        assertThrows(IllegalStateException::class.java) {
            taken.confirm(pack(), dose("2"), LATER)
        }
    }

    @Test
    fun skippedIntakeIsNotConfirmedImplicitly() {
        // Отмена пропуска — отдельное явное действие, и в первой версии её нет.
        val skipped = plannedIntake().skip(LATER)
        assertNull(skipped.taken?.amount)
        assertEquals(LATER, skipped.answer?.at)
        assertThrows(IllegalStateException::class.java) {
            skipped.confirm(pack(), dose("2"), LATER)
        }
        assertThrows(IllegalStateException::class.java) { skipped.miss(LATER) }
    }

    @Test
    fun repeatingTheSameAnswerChangesNothing() {
        val skipped = plannedIntake().skip(LATER)
        assertEquals(skipped.answer?.at, skipped.skip(LATER.plusSeconds(60)).answer?.at)
        val cancelled = plannedIntake().cancel(LATER)
        assertEquals(IntakeStatus.CANCELLED, cancelled.cancel(LATER).status)
    }

    @Test
    fun cancelledItemKeepsItsPlanAsHistory() {
        val cancelled = plannedIntake().cancel(LATER)
        assertEquals(dose("2"), cancelled.plannedAmount)
        assertEquals(FIRST_PLANNED_AT, cancelled.plannedAt)
        assertNull(cancelled.taken?.at)
    }

    @Test
    fun unsuppliedIntakeIsPlannedWithoutAPackage() {
        // План без источников всё равно порождает пункты, и они честно необеспечены (PLAN H1).
        val unsupplied = plannedIntake(plannedPackageId = null)
        assertFalse(unsupplied.isSupplied)
        assertNull(unsupplied.plannedPackageId)
        // Подтвердить его можно, назвав пачку: списать «неизвестно откуда» нельзя, а осознанно
        // выбранная пачка — обычный ответ человека. Плановой пачки у пункта так и не появится:
        // прошлое не переписывается ответом.
        val answered = unsupplied.confirm(pack(), dose("2"), LATER)
        assertFalse(answered.isSupplied)
        assertEquals(PACK, answered.taken?.packageId)
    }

    @Test
    fun unplannedIntakeHasNoPlanAtAll() {
        // У внепланового факта по типу нет полей курса и расписания; единственный статус — TAKEN.
        val fact = unplannedIntake()
        assertEquals(IntakeStatus.TAKEN, fact.status)
        assertEquals(PACK, fact.taken.packageId)
        assertEquals(dose("1"), fact.taken.amount)
    }

    @Test
    fun courseItemIsIdentifiedByItsRevisionAndScheduledSlot() {
        // Тождество пункта при повторной материализации окна (PLAN F4): курс, редакция и
        // назначенные дата со временем. Ответ их не переписывает.
        val answered = plannedIntake().confirm(pack(), dose("2"), LATER)
        assertEquals(COURSE, answered.courseId)
        assertEquals(Revision(1), answered.courseRevision)
        assertEquals(FIRST_SCHEDULED_ON, answered.slot.localDate)
        assertEquals(FIRST_SCHEDULED_TIME, answered.slot.localTime)
        assertEquals(FIRST_PLANNED_AT, answered.plannedAt)
    }

    @Test
    fun amountsAreMeasuredByTheIntakeUnit() {
        // Единица приёма — единица его плановой дозы, второго поля для неё нет.
        assertEquals(MILLILITRES, plannedIntake(plannedAmount = dose(millilitres("5"))).unitId)
        // Факт в другой единице к этому пункту не относится.
        assertThrows(IllegalArgumentException::class.java) {
            plannedIntake().confirm(pack(), dose(millilitres("5")), LATER)
        }
    }

    @Test
    fun takingZeroIsASkipAndNotAnIntake() {
        // Проверка переехала на саму дозу: собрать её из нуля нельзя, и до подтверждения дело
        // уже не доходит.
        assertThrows(IllegalArgumentException::class.java) { dose("0") }
    }
}
