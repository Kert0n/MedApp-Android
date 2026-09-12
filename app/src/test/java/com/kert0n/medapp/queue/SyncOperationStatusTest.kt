package com.kert0n.medapp.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * У операции очереди шесть состояний, и неопределённости среди них нет: повтор с неизвестным
 * исходом идёт тем же запросом, а «устарело» — не состояние, а переподготовка (PLAN E2, E3).
 * Применено и отказано — разные состояния, потому что зависимые и экран поступают с ними
 * по-разному; «ответ получен» — потому что с ним работник не ходит в сеть.
 */
class SyncOperationStatusTest {

    @Test
    fun thereAreExactlySixStates() {
        assertEquals(
            listOf("PENDING", "SENDING", "ANSWERED", "APPLIED", "REFUSED", "ACCESS_LOST"),
            SyncOperationStatus.entries.map { it.name }
        )
    }

    @Test
    fun closedStatesAreTheOnesTheWorkerNeverTouchesAgain() {
        assertTrue(SyncOperationStatus.entries.filter { it.isClosed }.map { it.name }
            .containsAll(listOf("APPLIED", "REFUSED", "ACCESS_LOST")))
        assertFalse(SyncOperationStatus.PENDING.isClosed)
        assertFalse(SyncOperationStatus.SENDING.isClosed)
        assertFalse(SyncOperationStatus.ANSWERED.isClosed)
    }
}
