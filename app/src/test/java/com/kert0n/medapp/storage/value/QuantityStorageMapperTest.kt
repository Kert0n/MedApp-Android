package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Количество хранится точно, а порядок по нему не требует `CAST(… AS REAL)`: ключ сортировки —
 * та же строка, дополненная нулями до предельной ширины величины (PLAN F3).
 */
class QuantityStorageMapperTest {

    @Test
    fun storedAmountKeepsEveryDigit() {
        assertEquals("0.1", tablets("0.1").toStorageAmount())
        assertEquals("1.000000", tablets("1.000000").toStorageAmount())
        assertEquals("1234567890123", tablets("1234567890123").toStorageAmount())
    }

    @Test
    fun storedAmountReadsBackAsTheSameQuantity() {
        val quantity = tablets("12.345678")
        assertEquals(quantity, storedQuantity(quantity.toStorageAmount(), quantity.unitId))
    }

    @Test
    fun sortKeyHasOneWidthForEveryAmount() {
        val width = Quantity.MAX_INTEGER_DIGITS + 1 + Quantity.SCALE
        for (amount in listOf("0", "0.1", "1", "9.999999", "1234567890123")) {
            assertEquals(width, tablets(amount).toStorageSortKey().length)
        }
    }

    @Test
    fun sortKeyOrdersTextuallyTheWayNumbersOrder() {
        val ascending = listOf("0", "0.000001", "0.1", "1", "1.5", "2", "10", "1234567890123")
        val keys = ascending.map { tablets(it).toStorageSortKey() }
        assertEquals(keys.sorted(), keys)
    }

    /** Одно и то же число в разных масштабах — один ключ: `1` и `1.000000` не расходятся. */
    @Test
    fun sameNumberInDifferentScalesGivesOneKey() {
        assertEquals(tablets("1").toStorageSortKey(), tablets("1.000000").toStorageSortKey())
    }

    /** Текстовый порядок ловит именно тот случай, на котором ломается сравнение без дополнения. */
    @Test
    fun tenIsAboveTwoUnlikePlainTextComparison() {
        assertTrue("10" < "2")
        assertTrue(tablets("10").toStorageSortKey() > tablets("2").toStorageSortKey())
    }
}
