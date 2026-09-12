package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Пара «изменение и его команда» живёт у службы: одна транзакция, и местной аптечке — ничего. */
class QueueServiceTest {

    private class Storage : QueueStorage {
        val enqueued = mutableListOf<QueuedCommand>()
        var transactions = 0
        override fun changes(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun nextDueAt(now: Instant): Instant? = null
        override suspend fun ready(now: Instant): List<StoredSyncOperation> = emptyList()
        override suspend fun medKit(id: Uuid): com.kert0n.medapp.domain.medkit.MedKitRef? = null
        override suspend fun take(id: Uuid, fresh: com.kert0n.medapp.network.pack.PackageSnapshot?, at: Instant): Take? = null
        override suspend fun answered(id: Uuid, answer: com.kert0n.medapp.network.server.RawResponse, at: Instant) = Unit
        override suspend fun defer(id: Uuid, reason: String, at: Instant, notBefore: Instant) = Unit
        override suspend fun settle(id: Uuid, settlement: Settlement, at: Instant) = Unit
        override suspend fun <T> transaction(block: suspend () -> T): T {
            transactions++
            return block()
        }
        override suspend fun enqueue(queued: QueuedCommand, at: Instant): SyncOperation {
            enqueued += queued
            return SyncOperation(queued.id, queued.command, enqueued.size.toLong(), at, 1)
        }
    }

    private val consume = QueuedCommand(INTAKE, PackageSyncCommand.Consume(PACK, dose("2"), INTAKE))

    private val published = medKit(publication = MedKit.Publication.PUBLISHED)

    @Test
    fun changeAndItsCommandGoInOneTransaction() = runTest {
        val storage = Storage()
        var changed = false
        val applied = QueueService(storage).change(published.ref, listOf(consume), EARLIER) {
            changed = true
            true
        }
        assertTrue(applied)
        assertTrue(changed)
        assertEquals(listOf(consume), storage.enqueued)
        assertEquals(1, storage.transactions)
    }

    @Test
    fun localKitGetsNoCommands() = runTest {
        val storage = Storage()

        assertTrue(QueueService(storage).change(medKit(publication = MedKit.Publication.LOCAL).ref, listOf(consume), EARLIER) { true })

        assertTrue(storage.enqueued.isEmpty())
    }

    @Test
    fun aChangeThatDidNotLandQueuesNothing() = runTest {
        val storage = Storage()

        assertFalse(QueueService(storage).change(published.ref, listOf(consume), EARLIER) { false })

        assertTrue(storage.enqueued.isEmpty())
    }
}
