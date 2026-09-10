package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.model.value.Quantity
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Выделение измеряется приёмами, и тупика больше нет (PLAN D5). */
class MaxDosesTest {

    private val availability = mapOf(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun stack(first: Int, second: Int) =
        activeCourse(sources = listOf(source(PACK, first), source(OTHER_PACK, second)))

    @Test
    fun doseTwoOutOfTwoSinglesGivesZeroDoses() {
        // Тот самый тупик: в таблетках человек выделил бы 1 + 1 и зажал ползунки при нулевом
        // покрытии. В дозах ответ ноль, не хватает одной дозы — честно и с выходом.
        val course = stack(first = 0, second = 0)
        val singles = mapOf(PACK to tablets("1"), OTHER_PACK to tablets("1"))
        assertEquals(0, maxDoses(PACK, course, requiredDoses = 14, availability = singles))
        assertEquals(0, maxDoses(OTHER_PACK, course, requiredDoses = 14, availability = singles))
    }

    @Test
    fun packageLimitsTheSliderWhenTheNeedIsLarger() {
        // 20 таблеток по два — десять доз, а нужно 28.
        assertEquals(10, maxDoses(PACK, stack(0, 0), requiredDoses = 28, availability = availability))
        assertEquals(6, maxDoses(OTHER_PACK, stack(0, 0), requiredDoses = 28, availability = availability))
    }

    @Test
    fun secondSliderIsClampedWhenTheFirstCoveredTheNeed() {
        // Первый источник закрыл потребность: второму выделять нечего, пока первый не уменьшат.
        val course = stack(first = 9, second = 0)
        assertEquals(0, maxDoses(OTHER_PACK, course, requiredDoses = 9, availability = availability))
        val loosened = stack(first = 5, second = 0)
        assertEquals(4, maxDoses(OTHER_PACK, loosened, requiredDoses = 9, availability = availability))
    }

    @Test
    fun sliderOfTheSourceItselfIgnoresItsOwnAllocation() {
        // Иначе уже выделенное вычиталось бы дважды и ползунок сползал бы при каждом открытии.
        assertEquals(4, maxDoses(PACK, stack(4, 5), requiredDoses = 9, availability = availability))
    }

    @Test
    fun overAllocatedStackDoesNotProduceANegativeLimit() {
        // Потребность уменьшилась после пропуска: предел ноль, а не «минус две дозы».
        assertEquals(0, maxDoses(OTHER_PACK, stack(9, 2), requiredDoses = 9, availability = availability))
    }

    @Test
    fun packageThatIsNotYetASourceIsAnsweredToo() {
        // «Сколько можно выделить, если подключить эту пачку» — тот же расчёт.
        val onlyFirst = activeCourse(sources = listOf(source(PACK, 5)))
        assertEquals(4, maxDoses(OTHER_PACK, onlyFirst, requiredDoses = 9, availability = availability))
    }

    @Test
    fun unknownAvailabilityKeepsTheLastAllocation() {
        // При требуемой сверке числовой предел недоступен: снижать выделение догадкой нельзя.
        assertEquals(5, maxDoses(PACK, stack(5, 4), requiredDoses = 28, availability = emptyMap()))
        assertEquals(0, maxDoses(OTHER_PACK, stack(5, 0), requiredDoses = 28, availability = emptyMap()))
    }

    @Test
    fun fractionalRemainderNeverBecomesADose() {
        // Пять таблеток по два — две дозы, пятая таблетка остаётся физическим остатком.
        val odd = mapOf(PACK to tablets("5"))
        assertEquals(2, maxDoses(PACK, stack(0, 0), requiredDoses = 28, availability = odd))
    }

    @Test
    fun sliderWithoutADoseIsNotDefined() {
        val noDose = course(sources = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            maxDoses(PACK, noDose, requiredDoses = 10, availability = availability)
        }
    }

    @Test
    fun fractionalDoseIsCountedAsWhole() {
        // Половина таблетки — законная доза; из двадцати таблеток это сорок приёмов.
        val halves = activeCourse(doseAmount = BigDecimal("0.5"), sources = listOf(source(PACK, 0)))
        assertEquals(40, maxDoses(PACK, halves, requiredDoses = 100, availability = availability))
        assertEquals(Quantity(BigDecimal("0.5"), tablets("1").unitId), halves.dose)
    }
}
