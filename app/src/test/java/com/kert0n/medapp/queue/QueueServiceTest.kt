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

    /** Просьба отправить: считаем, сколько раз и при скольких уже поставленных командах. */
    private class Sending : QueueSending {
        var asked = 0
        override fun soon() {
            asked++
        }
    }

    private val consume = QueuedCommand(INTAKE, PackageSyncCommand.Consume(PACK, dose("2"), INTAKE))

    private val published = medKit(publication = MedKit.Publication.PUBLISHED)

    @Test
    fun changeAndItsCommandGoInOneTransaction() = runTest {
        val storage = Storage()
        var changed = false
        val applied = QueueService(storage, Sending()).change(published.ref, listOf(consume), EARLIER) {
            changed = true
            true
        }
        assertTrue(applied)
        assertTrue(changed)
        assertEquals(listOf(consume), storage.enqueued)
        assertEquals(1, storage.transactions)
    }

    /** Поставленная команда уходит сразу: службу об этом просят, а не каждый сценарий помнит. */
    @Test
    fun aQueuedCommandIsSentRightAfterTheChange() = runTest {
        val sending = Sending()

        QueueService(Storage(), sending).change(published.ref, listOf(consume), EARLIER) { true }

        assertEquals(1, sending.asked)
    }

    @Test
    fun localKitGetsNoCommands() = runTest {
        val storage = Storage()
        val sending = Sending()

        assertTrue(QueueService(storage, sending).change(medKit(publication = MedKit.Publication.LOCAL).ref, listOf(consume), EARLIER) { true })

        assertTrue(storage.enqueued.isEmpty())
        assertEquals("местной аптечке отправлять нечего", 0, sending.asked)
    }

    @Test
    fun aChangeThatDidNotLandQueuesNothing() = runTest {
        val storage = Storage()
        val sending = Sending()

        assertFalse(QueueService(storage, sending).change(published.ref, listOf(consume), EARLIER) { false })

        assertTrue(storage.enqueued.isEmpty())
        assertEquals("записывать было некуда — и везти нечего", 0, sending.asked)
    }

    /** Изменение без команд серверу ничего не добавляет: будить отправку незачем. */
    @Test
    fun aChangeWithoutCommandsAsksForNothing() = runTest {
        val sending = Sending()

        assertTrue(QueueService(Storage(), sending).change(published.ref, emptyList(), EARLIER) { true })

        assertEquals(0, sending.asked)
    }
}
