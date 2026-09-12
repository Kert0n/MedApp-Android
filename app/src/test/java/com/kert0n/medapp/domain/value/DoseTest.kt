package com.kert0n.medapp.domain.value

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        assertEquals(TABLETS, Dose(tablets("2")).unit)
    }

    @Test
    fun doseTakenSeveralTimesIsAQuantity() {
        // Доза × число приёмов — это уже количество в единицах пачки, а не доза.
        assertTrue(Dose(tablets("2")) * 3.doses == tablets("6"))
        assertTrue(Dose(tablets("2")) * 0.doses == tablets("0"))
    }

    @Test
    fun remainderHoldsWholeDosesOnly() {
        // Пять таблеток по две — две дозы; делить на ноль здесь нечем.
        assertTrue(tablets("5").dosesIn(Dose(tablets("2"))) == 2.doses)
    }
}
