package com.kert0n.medapp.domain.value

import com.kert0n.medapp.fixture.TABLETS

import org.junit.Assert.assertEquals
import org.junit.Test

/** Словарные записи — значения домена, а не строки из ответа: имя обязано быть содержательным. */
class VocabularyTest {

    @Test
    fun unitAndFormKeepTheirServerIdentifiers() {
        assertEquals(TABLETS, QuantityUnit(TABLETS, "таблетки").id)
        assertEquals(TABLETS, DosageForm(TABLETS, "таблетки, покрытые оболочкой").id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankUnitNameIsRejected() {
        QuantityUnit(TABLETS, "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankFormNameIsRejected() {
        DosageForm(TABLETS, "")
    }
}
