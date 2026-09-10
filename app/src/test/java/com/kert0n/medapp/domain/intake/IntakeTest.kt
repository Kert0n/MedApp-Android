package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.FIRST_PLANNED_AT
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_ON
import com.kert0n.medapp.fixture.FIRST_SCHEDULED_TIME
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.plannedIntake
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
        val taken = planned.confirm(PACK, HOME_KIT, tablets("2"), LATER)
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
        val taken = plannedIntake().confirm(OTHER_PACK, SHARED_KIT, tablets("1"), LATER)
        assertEquals(FIRST_PLANNED_AT, taken.plannedAt)
        assertEquals(FIRST_SCHEDULED_ON, taken.slot.localDate)
        assertEquals(FIRST_SCHEDULED_TIME, taken.slot.localTime)
        assertEquals(tablets("2"), taken.plannedAmount)
        assertEquals(PACK, taken.plannedPackageId)
        // А фактические пачка, аптечка и количество — те, что назвал человек.
        assertEquals(OTHER_PACK, taken.taken?.packageId)
        assertEquals(SHARED_KIT, taken.taken?.medKitId)
        assertEquals(tablets("1"), taken.taken?.amount)
    }

    @Test
    fun factualAmountMayDifferFromThePlanned() {
        val taken = plannedIntake().confirm(PACK, HOME_KIT, tablets("3"), LATER)
        assertEquals(tablets("3"), taken.taken?.amount)
        assertEquals(tablets("2"), taken.plannedAmount)
    }

    @Test
    fun missedItemIsStillConfirmable() {
        // Поздний ответ проверяет текущий источник и остаток заново, но пункт остаётся тем же.
        val missed = plannedIntake().miss(LATER)
        assertEquals(IntakeStatus.MISSED, missed.status)
        val late = missed.confirm(PACK, HOME_KIT, tablets("2"), LATER.plusSeconds(3600))
        assertEquals(IntakeStatus.TAKEN, late.status)
    }

    @Test
    fun confirmedIntakeIsNotConfirmedTwice() {
        // Второе подтверждение — второй факт со своим идентификатором, а не тот же самый.
        val taken = plannedIntake().confirm(PACK, HOME_KIT, tablets("2"), LATER)
        assertThrows(IllegalStateException::class.java) {
            taken.confirm(PACK, HOME_KIT, tablets("2"), LATER)
        }
    }

    @Test
    fun skippedIntakeIsNotConfirmedImplicitly() {
        // Отмена пропуска — отдельное явное действие, и в первой версии её нет.
        val skipped = plannedIntake().skip(LATER)
        assertNull(skipped.taken?.amount)
        assertEquals(LATER, skipped.answer?.at)
        assertThrows(IllegalStateException::class.java) {
            skipped.confirm(PACK, HOME_KIT, tablets("2"), LATER)
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
        assertEquals(tablets("2"), cancelled.plannedAmount)
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
        val answered = unsupplied.confirm(PACK, HOME_KIT, tablets("2"), LATER)
        assertFalse(answered.isSupplied)
        assertEquals(PACK, answered.taken?.packageId)
    }

    @Test
    fun unplannedIntakeHasNoPlanAtAll() {
        // Планировать разовый приём нечем: ни курса, ни расписания, которое его породило. Раньше
        // это утверждал `require` над сочетанием четырёх `null`, теперь — тип: полей плана у
        // внепланового факта нет, и статуса, кроме TAKEN, у него не бывает.
        val fact = unplannedIntake()
        assertEquals(IntakeStatus.TAKEN, fact.status)
        assertEquals(PACK, fact.taken.packageId)
        assertEquals(tablets("1"), fact.taken.amount)
    }

    @Test
    fun courseItemIsIdentifiedByItsRevisionAndScheduledSlot() {
        // Тождество пункта при повторной материализации окна (PLAN F4): курс, редакция и
        // назначенные дата со временем. Ответ их не переписывает.
        val answered = plannedIntake().confirm(PACK, HOME_KIT, tablets("2"), LATER)
        assertEquals(COURSE, answered.courseId)
        assertEquals(1L, answered.courseRevision)
        assertEquals(FIRST_SCHEDULED_ON, answered.slot.localDate)
        assertEquals(FIRST_SCHEDULED_TIME, answered.slot.localTime)
        assertEquals(FIRST_PLANNED_AT, answered.plannedAt)
    }

    @Test
    fun amountsAreMeasuredByTheIntakeUnit() {
        // Единица пишется на момент события, и величина в другой единице к ней не относится.
        assertThrows(IllegalArgumentException::class.java) {
            plannedIntake(plannedAmount = millilitres("5"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            plannedIntake().confirm(PACK, HOME_KIT, millilitres("5"), LATER)
        }
    }

    @Test
    fun takingZeroIsASkipAndNotAnIntake() {
        assertThrows(IllegalArgumentException::class.java) {
            plannedIntake().confirm(PACK, HOME_KIT, tablets("0"), LATER)
        }
    }
}
