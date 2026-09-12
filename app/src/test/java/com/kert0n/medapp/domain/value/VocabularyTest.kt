package com.kert0n.medapp.domain.value

import com.kert0n.medapp.fixture.CAPSULE_FORM
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLETS_ID
import com.kert0n.medapp.fixture.TABLET_FORM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Словарные записи — значения домена, а не строки из ответа: имя обязано быть содержательным. */
class VocabularyTest {

    @Test
    fun unitAndFormKeepTheirServerIdentifiers() {
        assertEquals(TABLETS_ID, QuantityUnit(TABLETS_ID, "таблетки").id)
        assertEquals(TABLETS_ID, DosageForm(TABLETS_ID, "таблетки, покрытые оболочкой").id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankUnitNameIsRejected() {
        QuantityUnit(TABLETS_ID, "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankFormNameIsRejected() {
        DosageForm(TABLETS_ID, "")
    }

    @Test
    fun renamedUnitIsTheSameUnit() {
        // Сосед переименовал «таб» в «таблетки» на сервере: количества, записанные до этого, и
        // записанные после, считаются вместе — иначе смена имени ломала бы арифметику и историю.
        val before = QuantityUnit(TABLETS_ID, "таб")
        val after = QuantityUnit(TABLETS_ID, "таблетки")
        assertEquals(before, after)
        assertEquals(before.hashCode(), after.hashCode())
        assertEquals(
            Quantity(java.math.BigDecimal(5), after),
            Quantity(java.math.BigDecimal(2), before) + Quantity(java.math.BigDecimal(3), after)
        )
        assertEquals(DosageForm(TABLETS_ID, "таблетки"), DosageForm(TABLETS_ID, "таблетки, покрытые оболочкой"))
    }

    @Test
    fun snapshotAnswersByIdentifierAndMissesHonestly() {
        // Промах — «снимок старее того, кто назвал единицу», а не «такой нет»: ответ `null`, и
        // что с ним делать, решает тот, кто снимок держит.
        val words = Vocabulary(listOf(TABLETS), listOf(TABLET_FORM))
        assertEquals(TABLETS, words.unit(TABLETS_ID))
        assertNull(words.unit(MILLILITRES.id))
        assertNull(words.form(CAPSULE_FORM.id))
    }

    @Test
    fun snapshotWithTheSameEntriesIsTheSameSnapshot() {
        assertEquals(
            Vocabulary(listOf(TABLETS, MILLILITRES), listOf(TABLET_FORM)),
            Vocabulary(listOf(MILLILITRES, TABLETS), listOf(TABLET_FORM))
        )
    }

    @Test
    fun anEntryListedTwiceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Vocabulary(listOf(TABLETS, TABLETS), emptyList())
        }
    }
}
