package com.kert0n.medapp.domain.model.intake

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
        assertEquals(LATER, taken.takenAt)
        assertEquals(LATER, taken.respondedAt)
    }

    @Test
    fun answerDoesNotRewriteThePlan() {
        // Пункт порождён редакцией расписания, и ответ не переписывает ни назначенное время,
        // ни плановую дозу, ни плановую пачку.
        val taken = plannedIntake().confirm(OTHER_PACK, SHARED_KIT, tablets("1"), LATER)
        assertEquals(FIRST_PLANNED_AT, taken.plannedAt)
        assertEquals(FIRST_SCHEDULED_ON, taken.scheduledOn)
        assertEquals(FIRST_SCHEDULED_TIME, taken.scheduledTime)
        assertEquals(tablets("2"), taken.plannedAmount)
        assertEquals(PACK, taken.plannedPackageId)
        // А фактические пачка, аптечка и количество — те, что назвал человек.
        assertEquals(OTHER_PACK, taken.takenPackageId)
        assertEquals(SHARED_KIT, taken.medKitId)
        assertEquals(tablets("1"), taken.takenAmount)
    }

    @Test
    fun factualAmountMayDifferFromThePlanned() {
        val taken = plannedIntake().confirm(PACK, HOME_KIT, tablets("3"), LATER)
        assertEquals(tablets("3"), taken.takenAmount)
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
        assertNull(skipped.takenAmount)
        assertEquals(LATER, skipped.respondedAt)
        assertThrows(IllegalStateException::class.java) {
            skipped.confirm(PACK, HOME_KIT, tablets("2"), LATER)
        }
        assertThrows(IllegalStateException::class.java) { skipped.miss(LATER) }
    }

    @Test
    fun repeatingTheSameAnswerChangesNothing() {
        val skipped = plannedIntake().skip(LATER)
        assertEquals(skipped.respondedAt, skipped.skip(LATER.plusSeconds(60)).respondedAt)
        val cancelled = plannedIntake().cancel(LATER)
        assertEquals(IntakeStatus.CANCELLED, cancelled.cancel(LATER).status)
    }

    @Test
    fun cancelledItemKeepsItsPlanAsHistory() {
        val cancelled = plannedIntake().cancel(LATER)
        assertEquals(tablets("2"), cancelled.plannedAmount)
        assertEquals(FIRST_PLANNED_AT, cancelled.plannedAt)
        assertNull(cancelled.takenAt)
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
        assertEquals(PACK, answered.takenPackageId)
    }

    @Test
    fun unplannedIntakeExistsOnlyAsAFact() {
        val fact = unplannedIntake()
        assertEquals(IntakeStatus.TAKEN, fact.status)
        assertNull(fact.courseId)
        assertNull(fact.plannedAt)
        // Запланировать разовый приём нечем: ни курса, ни расписания, которое его породило.
        assertThrows(IllegalArgumentException::class.java) {
            plannedIntake(courseId = null, courseRevision = null, scheduledOn = null, scheduledTime = null)
        }
    }

    @Test
    fun courseItemIsIdentifiedByItsRevisionAndScheduledTime() {
        // Без них строка не отличима от внепланового факта, а повторная материализация окна
        // перестала бы быть идемпотентной.
        assertThrows(IllegalArgumentException::class.java) { plannedIntake(courseRevision = null) }
        assertThrows(IllegalArgumentException::class.java) { plannedIntake(scheduledOn = null) }
        assertThrows(IllegalArgumentException::class.java) { plannedIntake(scheduledTime = null) }
    }

    @Test
    fun plannedItemKnowsWhenItHappens() {
        assertThrows(IllegalArgumentException::class.java) { plannedIntake(plannedAt = null) }
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
