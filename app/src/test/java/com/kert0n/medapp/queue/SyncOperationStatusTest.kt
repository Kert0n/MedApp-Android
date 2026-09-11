package com.kert0n.medapp.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * У операции очереди пять состояний, и неопределённости среди них нет: повтор с неизвестным
 * исходом идёт тем же запросом, а «устарело» — не состояние, а переподготовка (PLAN E2, E3).
 * Применено и отказано — разные состояния, потому что зависимые и экран поступают с ними
 * по-разному.
 */
class SyncOperationStatusTest {

    @Test
    fun thereAreExactlyFiveStates() {
        assertEquals(
            listOf("PENDING", "SENDING", "APPLIED", "REFUSED", "ACCESS_LOST"),
            SyncOperationStatus.entries.map { it.name }
        )
    }

    @Test
    fun closedStatesAreTheOnesTheWorkerNeverTouchesAgain() {
        assertTrue(SyncOperationStatus.entries.filter { it.isClosed }.map { it.name }
            .containsAll(listOf("APPLIED", "REFUSED", "ACCESS_LOST")))
        assertFalse(SyncOperationStatus.PENDING.isClosed)
        assertFalse(SyncOperationStatus.SENDING.isClosed)
    }
}
