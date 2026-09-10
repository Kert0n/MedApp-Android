package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.model.course.CourseSource
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Избыток выделения снимается с конца стека: сверху расходуют, снизу освобождают (PLAN D5). */
class TrimToRequiredTest {

    private val stack = listOf(source(PACK, 5), source(OTHER_PACK, 4))

    @Test
    fun skipReleasesADoseFromTheEndOfTheStack() {
        // Пропуск уменьшил потребность с девяти до восьми: освободилась доза нижнего источника,
        // а тот, из которого человек принимает, не тронут.
        assertEquals(listOf(doses(5), doses(3)), trimToRequired(stack, requiredDoses = doses(8)).map { it.allocatedDoses })
    }

    @Test
    fun trimmingWalksUpTheStackWhenTheTailIsExhausted() {
        assertEquals(listOf(doses(4), doses(0)), trimToRequired(stack, requiredDoses = doses(4)).map { it.allocatedDoses })
        assertEquals(listOf(doses(0), doses(0)), trimToRequired(stack, requiredDoses = doses(0)).map { it.allocatedDoses })
    }

    @Test
    fun allocationWithinTheNeedIsLeftAlone() {
        assertEquals(stack, trimToRequired(stack, requiredDoses = doses(9)))
        assertEquals(stack, trimToRequired(stack, requiredDoses = doses(28)))
    }

    @Test
    fun trimmingNeverRaisesAnAllocation() {
        // Автоматического увеличения нет: выделение — решение человека.
        val small = listOf(source(PACK, 1))
        assertEquals(listOf(doses(1)), trimToRequired(small, requiredDoses = doses(28)).map { it.allocatedDoses })
    }

    @Test
    fun emptyStackSurvivesTheTrim() {
        assertEquals(emptyList<CourseSource>(), trimToRequired(emptyList(), requiredDoses = doses(5)))
    }

    @Test
    fun sourceOrderAndPackagesAreUntouched() {
        val trimmed = trimToRequired(stack, requiredDoses = doses(6))
        assertEquals(listOf(PACK, OTHER_PACK), trimmed.map { it.packageId })
    }
}
