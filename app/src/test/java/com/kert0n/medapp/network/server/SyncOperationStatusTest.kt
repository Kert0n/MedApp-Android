package com.kert0n.medapp.network.server

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * У операции очереди четыре состояния, и неопределённости среди них нет: повтор безопасен
 * замороженным предусловием, а не журналом исходов (PLAN E2, E3).
 */
class SyncOperationStatusTest {

    @Test
    fun thereAreExactlyFourStates() {
        assertEquals(
            listOf("PENDING", "SENDING", "DONE", "ACCESS_LOST"),
            SyncOperationStatus.entries.map { it.name }
        )
    }
}
