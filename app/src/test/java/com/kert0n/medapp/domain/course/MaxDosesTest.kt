package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Выделение измеряется приёмами, и тупика больше нет (PLAN D5). */
class MaxDosesTest {

    private val availability = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun stack(first: Int, second: Int) =
        activeCourse(sources = listOf(source(PACK, first), source(OTHER_PACK, second)))

    @Test
    fun doseTwoOutOfTwoSinglesGivesZeroDoses() {
        // Тот самый тупик: в таблетках человек выделил бы 1 + 1 и зажал ползунки при нулевом
        // покрытии. В дозах ответ ноль, не хватает одной дозы — честно и с выходом.
        val course = stack(first = 0, second = 0)
        val singles = availability(PACK to tablets("1"), OTHER_PACK to tablets("1"))
        assertEquals(doses(0), maxDoses(PACK, course, requiredDoses = doses(14), availability = singles))
        assertEquals(doses(0), maxDoses(OTHER_PACK, course, requiredDoses = doses(14), availability = singles))
    }

    @Test
    fun packageLimitsTheSliderWhenTheNeedIsLarger() {
        // 20 таблеток по два — десять доз, а нужно 28.
        assertEquals(doses(10), maxDoses(PACK, stack(0, 0), requiredDoses = doses(28), availability = availability))
        assertEquals(doses(6), maxDoses(OTHER_PACK, stack(0, 0), requiredDoses = doses(28), availability = availability))
    }

    @Test
    fun secondSliderIsClampedWhenTheFirstCoveredTheNeed() {
        // Первый источник закрыл потребность: второму выделять нечего, пока первый не уменьшат.
        val course = stack(first = 9, second = 0)
        assertEquals(doses(0), maxDoses(OTHER_PACK, course, requiredDoses = doses(9), availability = availability))
        val loosened = stack(first = 5, second = 0)
        assertEquals(doses(4), maxDoses(OTHER_PACK, loosened, requiredDoses = doses(9), availability = availability))
    }

    @Test
    fun sliderOfTheSourceItselfIgnoresItsOwnAllocation() {
        // Иначе уже выделенное вычиталось бы дважды и ползунок сползал бы при каждом открытии.
        assertEquals(doses(4), maxDoses(PACK, stack(4, 5), requiredDoses = doses(9), availability = availability))
    }

    @Test
    fun overAllocatedStackDoesNotProduceANegativeLimit() {
        // Потребность уменьшилась после пропуска: предел ноль, а не «минус две дозы».
        assertEquals(doses(0), maxDoses(OTHER_PACK, stack(9, 2), requiredDoses = doses(9), availability = availability))
    }

    @Test
    fun packageThatIsNotYetASourceIsAnsweredToo() {
        // «Сколько можно выделить, если подключить эту пачку» — тот же расчёт.
        val onlyFirst = activeCourse(sources = listOf(source(PACK, 5)))
        assertEquals(doses(4), maxDoses(OTHER_PACK, onlyFirst, requiredDoses = doses(9), availability = availability))
    }

    @Test
    fun unknownAvailabilityKeepsTheLastAllocation() {
        // При требуемой сверке числовой предел недоступен: снижать выделение догадкой нельзя.
        assertEquals(doses(5), maxDoses(PACK, stack(5, 4), requiredDoses = doses(28), availability = Availability.nothingKnown))
        assertEquals(doses(0), maxDoses(OTHER_PACK, stack(5, 0), requiredDoses = doses(28), availability = Availability.nothingKnown))
    }

    @Test
    fun fractionalRemainderNeverBecomesADose() {
        // Пять таблеток по два — две дозы, пятая таблетка остаётся физическим остатком.
        val odd = availability(PACK to tablets("5"))
        assertEquals(doses(2), maxDoses(PACK, stack(0, 0), requiredDoses = doses(28), availability = odd))
    }

    @Test
    fun fractionalDoseIsCountedAsWhole() {
        // Половина таблетки — законная доза; из двадцати таблеток это сорок приёмов.
        val halves = activeCourse(doseAmount = BigDecimal("0.5"), sources = listOf(source(PACK, 0)))
        assertEquals(doses(40), maxDoses(PACK, halves, requiredDoses = doses(100), availability = availability))
        assertEquals(Quantity(BigDecimal("0.5"), tablets("1").unitId), halves.dose)
    }
}
