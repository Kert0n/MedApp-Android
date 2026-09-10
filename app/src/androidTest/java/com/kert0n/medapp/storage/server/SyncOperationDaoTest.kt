package com.kert0n.medapp.storage.server

import android.database.sqlite.SQLiteConstraintException
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.rejectedByDatabase
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.medkit.MedKitSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.server.PreparedRequest
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.SyncOperation
import com.kert0n.medapp.network.server.SyncOperationStatus
import com.kert0n.medapp.storage.database.MedAppDatabase
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Очередь и её зависимости лежат в базе: номер выдаёт она, порядок по одной пачке строится
 * запросом, а нечитаемая команда не роняет разбор всей очереди (PLAN E2, F1, F4).
 */
class SyncOperationDaoTest {

    private lateinit var database: MedAppDatabase
    private val queue get() = database.syncOperations()

    private val first: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000091")
    private val second: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000092")
    private val third: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000093")
    private val createdAt: Instant = Instant.parse("2026-09-10T12:00:00Z")

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun enqueuedOperationComesBackWithItsCommand() = runTest {
        val command = PackageSyncCommand.Consume(PACK, dose("1.5"), INTAKE, claimAfter = tablets("4"))
        val enqueued = queue.enqueue(first, command, createdAt)

        val restored = readable(first)
        assertEquals(enqueued, restored)
        assertEquals(command, restored.command)
        assertEquals(SyncOperationStatus.PENDING, restored.status)
    }

    @Test
    fun sequenceIsHandedOutByTheDatabaseAndGrows() = runTest {
        queue.enqueue(first, PackageSyncCommand.Delete(PACK), createdAt)
        queue.enqueue(second, MedKitSyncCommand.Leave(SHARED_KIT), createdAt)

        assertEquals(listOf(0L, 1L), queue.all().map { it.operation.sequence })
    }

    /** Номер уникален: гонка двух постановок отвергается базой, а не остаётся незамеченной. */
    @Test
    fun twoOperationsCannotShareOneSequence() = runTest {
        val enqueued = queue.enqueue(first, PackageSyncCommand.Delete(PACK), createdAt)
        val clash = enqueued.let {
            SyncOperation(
                id = second,
                command = PackageSyncCommand.Delete(OTHER_PACK),
                sequence = it.sequence,
                createdAt = createdAt,
                payloadVersion = SyncCommandStorageConverter.PAYLOAD_VERSION
            )
        }

        val refusal = rejectedByDatabase { queue.insert(clash.toStorageEntity()) }
        assertTrue("$refusal", refusal is SQLiteConstraintException)
    }

    @Test
    fun simultaneousEnqueueFromTwoCoroutinesGivesTwoDistinctNumbers() = runBlocking {
        val start = CompletableDeferred<Unit>()
        val put = listOf(first to PACK, second to OTHER_PACK).map { (id, packageId) ->
            async(Dispatchers.IO) {
                start.await()
                queue.enqueue(id, PackageSyncCommand.Delete(packageId), createdAt)
            }
        }
        start.complete(Unit)
        val numbers = put.awaitAll().map { it.sequence }

        assertEquals(2, numbers.distinct().size)
        assertEquals(listOf(0L, 1L), numbers.sorted())
    }

    /** Порядок по одной упаковке строится запросом: команды аптечки в него не попадают. */
    @Test
    fun orderOfOnePackageIsAQueryAndNotADomainFunction() = runTest {
        queue.enqueue(first, PackageSyncCommand.CorrectStock(PACK, tablets("10")), createdAt)
        queue.enqueue(second, MedKitSyncCommand.Create(HOME_KIT), createdAt)
        queue.enqueue(third, PackageSyncCommand.ReleaseClaim(PACK), createdAt)

        val ofPack = queue.ofPackage(PACK).map { it.operation.id }
        assertEquals(listOf(first, third), ofPack)
        assertNull(queue.find(second)!!.operation.packageId)
    }

    @Test
    fun dependenciesTravelInTheirOwnTable() = runTest {
        queue.enqueue(first, MedKitSyncCommand.Create(HOME_KIT), createdAt)
        queue.enqueue(
            second,
            PackageSyncCommand.Create(PACK, HOME_KIT, tablets("20"), pack().facts.shared),
            createdAt,
            dependsOn = setOf(first)
        )

        assertEquals(listOf(first), queue.dependenciesOf(second))
        assertEquals(setOf(first), readable(second).dependsOn)
    }

    @Test
    fun dependencyOnAnUnknownOperationIsRefused() = runTest {
        val refusal = rejectedByDatabase {
            queue.insertDependencies(listOf(SyncOperationDependencyStorageEntity(first, second)))
        }
        assertTrue("$refusal", refusal is SQLiteConstraintException)
    }

    @Test
    fun preparedRequestIsStoredWholeIncludingPreconditions() = runTest {
        queue.enqueue(first, PackageSyncCommand.CorrectStock(PACK, tablets("10")), createdAt)
        val prepared = PreparedRequest(
            method = "PATCH",
            path = "/drugs/$PACK",
            query = mapOf("mode" to "absolute"),
            body = """{"amount":"10"}""",
            drugVersion = ResourceVersion(7),
            claimsVersion = ResourceVersion(3),
            quantityBefore = tablets("12"),
            mineBefore = tablets("2"),
            preparedAt = createdAt
        )
        val frozen = readable(first).let {
            SyncOperation(
                id = it.id,
                command = it.command,
                sequence = it.sequence,
                createdAt = it.createdAt,
                payloadVersion = it.payloadVersion,
                prepared = prepared,
                status = SyncOperationStatus.SENDING
            )
        }
        queue.update(frozen.toStorageEntity())

        val restored = readable(first)
        assertEquals(prepared, restored.prepared)
        assertEquals(SyncOperationStatus.SENDING, restored.status)
    }

    @Test
    fun settlingRecordsStatusErrorAndAttempt() = runTest {
        queue.enqueue(first, PackageSyncCommand.Delete(PACK), createdAt)
        val at = createdAt.plusSeconds(30)

        queue.settle(first, SyncOperationStatus.NEEDS_RECOUNT, "нет ответа", at, attempted = 1)

        val stored = requireNotNull(queue.find(first)).operation
        assertEquals(SyncOperationStatus.NEEDS_RECOUNT, stored.status)
        assertEquals("нет ответа", stored.lastError)
        assertEquals(at, stored.lastTriedAt)
        assertEquals(1, stored.attempts)
    }

    /** Незакрытые — те, чей исход ещё не установлен: свёртка остатка берёт именно их (PLAN E1). */
    @Test
    fun unclosedOperationsExcludeTheSettledOnes() = runTest {
        queue.enqueue(first, PackageSyncCommand.CorrectStock(PACK, tablets("10")), createdAt)
        queue.enqueue(second, PackageSyncCommand.Consume(PACK, dose("1"), INTAKE), createdAt)
        queue.settle(first, SyncOperationStatus.DONE)

        val unclosed = queue.unclosedOfPackage(PACK)
        assertEquals(listOf(second), unclosed.map { it.operation.id })
    }

    /**
     * Строка с чужой версией payload не собирается в операцию и не роняет очередь: вызывающий
     * переведёт её в `CONFLICT` (PLAN F4).
     */
    @Test
    fun operationWithForeignPayloadVersionReadsAsUnreadable() = runTest {
        val enqueued = queue.enqueue(first, PackageSyncCommand.Delete(PACK), createdAt)
        val stored = enqueued.toStorageEntity()
        queue.update(
            SyncOperationStorageEntity(
                id = stored.id,
                kind = stored.kind,
                payload = stored.payload,
                payloadVersion = stored.payloadVersion + 1,
                sequence = stored.sequence,
                status = stored.status,
                attempts = stored.attempts,
                createdAt = stored.createdAt,
                packageId = stored.packageId
            )
        )

        val stale = unreadable(first)
        assertTrue(stale.reason, stale.reason.contains("версии"))
        assertEquals(1, queue.all().size)
    }

    /**
     * Повреждённым может быть не только payload команды: параметры подготовленного запроса
     * восстанавливаются тем же разбором, и раньше они падали мимо защиты — вместе с поиском
     * нечитаемых строк, написанным ровно для таких случаев.
     */
    @Test
    fun damagedPreparedRequestMakesTheWholeRowUnreadable() = runTest {
        queue.enqueue(first, PackageSyncCommand.Delete(PACK), createdAt)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE sync_operations SET prepared_method = 'DELETE', prepared_path = '/drugs/$PACK', " +
                "prepared_query = 'не json', prepared_at = 0 WHERE id = '$first'"
        )

        val damaged = unreadable(first)

        assertEquals(first, damaged.id)
        assertEquals(listOf(damaged), queue.all().mapNotNull { it.toDomain() as? StoredSyncOperation.Unreadable })
    }

    private suspend fun readable(id: Uuid): SyncOperation =
        (requireNotNull(queue.find(id)).toDomain() as StoredSyncOperation.Readable).operation

    private suspend fun unreadable(id: Uuid): StoredSyncOperation.Unreadable =
        requireNotNull(queue.find(id)).toDomain() as StoredSyncOperation.Unreadable
}
