package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.doses
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.availability
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Предел выделения пачки считается в целых дозах, и тупика «1 + 1 при дозе 2» нет (PLAN D5). */
class CourseMaxDosesTest {

    private val home = pack(id = PACK)

    private val dacha = pack(id = OTHER_PACK, quantity = tablets("12"))

    private val availability = availability(PACK to tablets("20"), OTHER_PACK to tablets("12"))

    private fun twoPacks(first: Int, second: Int) =
        activeCourse(sources = listOf(source(PACK, first), source(OTHER_PACK, second)))

    private fun Course.limit(
        pkg: Package,
        required: Int,
        availability: Availability = this@CourseMaxDosesTest.availability
    ) = maxDoses(pkg.ref, required.doses, availability)

    @Test
    fun doseTwoOutOfTwoSinglesGivesZeroDoses() {
        // В таблетках человек выделил бы 1 + 1 при нулевом покрытии; в дозах ответ честно ноль.
        val singles = availability(PACK to tablets("1"), OTHER_PACK to tablets("1"))
        assertEquals(0.doses, twoPacks(0, 0).limit(home, required = 14, availability = singles))
        assertEquals(
            0.doses,
            twoPacks(0, 0).limit(dacha, required = 14, availability = singles)
        )
    }

    @Test
    fun packageLimitsTheSliderWhenTheNeedIsLarger() {
        // 20 таблеток по две — десять доз, а нужно 28.
        assertEquals(10.doses, twoPacks(0, 0).limit(home, required = 28))
        assertEquals(6.doses, twoPacks(0, 0).limit(dacha, required = 28))
    }

    @Test
    fun secondSliderIsClampedWhenTheFirstCoveredTheNeed() {
        // Первая пачка закрыла потребность: второй выделять нечего, пока первую не уменьшат.
        assertEquals(0.doses, twoPacks(9, 0).limit(dacha, required = 9))
        assertEquals(4.doses, twoPacks(5, 0).limit(dacha, required = 9))
    }

    @Test
    fun sliderOfTheSourceItselfIgnoresItsOwnAllocation() {
        // Иначе выделенное вычиталось бы дважды, и ползунок сползал бы при каждом открытии.
        assertEquals(4.doses, twoPacks(4, 5).limit(home, required = 9))
    }

    @Test
    fun overAllocatedMedicineDoesNotProduceANegativeLimit() {
        // Потребность уменьшилась после пропуска: предел ноль, а не «минус две дозы».
        assertEquals(0.doses, twoPacks(9, 2).limit(dacha, required = 9))
    }

    @Test
    fun packageThatIsNotYetASourceIsAnsweredToo() {
        // «Сколько можно выделить, если подключить эту пачку» — тот же расчёт.
        val onlyFirst = activeCourse(sources = listOf(source(PACK, 5)))
        assertEquals(4.doses, onlyFirst.limit(dacha, required = 9))
    }

    @Test
    fun fractionalRemainderNeverBecomesADose() {
        // Пять таблеток по две — две дозы; пятая таблетка остаётся физическим остатком.
        val odd = availability(PACK to tablets("5"))
        assertEquals(2.doses, twoPacks(0, 0).limit(home, required = 28, availability = odd))
    }

    @Test
    fun fractionalDoseIsCountedAsWhole() {
        // Половина таблетки — законная доза; из двадцати таблеток это сорок приёмов.
        val found = activeCourse(doseAmount = BigDecimal("0.5"), sources = listOf(source(PACK, 0)))
            .maxDoses(home.ref, 100.doses, availability)
        assertEquals(40.doses, found)
    }
}
