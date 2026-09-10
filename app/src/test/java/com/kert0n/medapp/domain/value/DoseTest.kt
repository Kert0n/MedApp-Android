package com.kert0n.medapp.domain.value

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Доза — сколько принимают за раз, и нулём она не бывает. Правило живёт на величине, поэтому его
 * соблюдает любой путь: назначение курса, приём, расход пачки, восстановление сохранённого.
 */
class DoseTest {

    @Test
    fun zeroIsNotADose() {
        // Нулевая доза — не лечение, а деление на ноль в обеспечении.
        assertThrows(IllegalArgumentException::class.java) { Dose(Quantity.zero(TABLETS)) }
    }

    @Test
    fun theUnitComesFromTheAmount() {
        assertEquals(TABLETS, Dose(tablets("2")).unitId)
    }

    @Test
    fun doseTakenSeveralTimesIsAQuantity() {
        // Доза × число приёмов — это уже количество в единицах пачки, а не доза.
        assertEquals(tablets("6"), Dose(tablets("2")) * doses(3))
        assertEquals(tablets("0"), Dose(tablets("2")) * doses(0))
    }

    @Test
    fun remainderHoldsWholeDosesOnly() {
        // Пять таблеток по две — две дозы; делить на ноль здесь нечем.
        assertEquals(doses(2), tablets("5").dosesIn(Dose(tablets("2"))))
    }
}
