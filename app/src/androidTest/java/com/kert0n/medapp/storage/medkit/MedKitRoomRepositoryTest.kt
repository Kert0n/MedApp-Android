package com.kert0n.medapp.storage.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.medKitRepository
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.pack.toDomain
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.storage.database.MedAppDatabase
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Передача ответственности серверу — одна транзакция: аптечка становится опубликованной вместе с
 * первыми подтверждёнными остатками и версиями своих пачек (PLAN E5).
 */
class MedKitRoomRepositoryTest {

    private lateinit var database: MedAppDatabase
    private lateinit var medKits: MedKitRoomRepository

    private val at: Instant = Instant.parse("2026-09-10T12:00:00Z")

    /** Разрешённый снимок — таким его отдаёт резолвер очереди или сценарий публикации. */
    private fun snapshot(medKitId: Uuid = HOME_KIT): PackageSnapshot = medAppJson.decodeFromString(
        PackageSnapshotNetworkDTO.serializer(),
        """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"18.000000","quantityUnitId":"${TABLETS.id}",
         "formTypeId":"${TABLET_FORM.id}","medKitId":"$medKitId","version":3},
         "reservations":{"total":"0.000000","version":1}}
        """
    ).toDomain(VOCABULARY, medKit(id = medKitId, publication = MedKit.Publication.PUBLISHED).ref, addedAt = at, observedAt = at)

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        medKits = database.medKitRepository()
        database.packageRepository().add(pack(quantity = tablets("20")))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /** Между попытками приняли две таблетки: остаток после переключения — серверный, 18. */
    @Test
    fun publishingSwitchesTheKitAndTakesTheServerState() = runTest {
        medKits.published(medKit().publish(), listOf(snapshot()), at)

        assertEquals(MedKit.Publication.PUBLISHED, requireNotNull(medKits.find(HOME_KIT)).publication)
        assertEquals(tablets("18"), requireNotNull(database.packageRepository().find(PACK)).quantity)
        assertEquals(ResourceVersion(3), requireNotNull(database.packages().find(PACK)).pack.syncState().version)
    }

    /**
     * Момент сверки — своим методом: он принадлежит доставке, а не аптечке, и нужен экрану
     * состояния синхронизации (PLAN E4, H3 №28).
     */
    @Test
    fun theMomentOfTheLastSyncIsObservedByItsOwnMethod() = runTest {
        assertNull(medKits.observeSyncedAt(HOME_KIT).first())

        medKits.applyServerParticipants(HOME_KIT, participantCount = 2, syncedAt = at)

        assertEquals(at, medKits.observeSyncedAt(HOME_KIT).first())
    }

    /**
     * Переименование меняет названные сведения и только их: публикация и число участников
     * остаются нынешними, хотя экран мог прочитать аптечку до того, как к ней присоединились
     * (PLAN F5).
     *
     * Красная проверка: сохранять правку полной записью — общая аптечка станет локальной с одним
     * участником.
     */
    @Test
    fun describingChangesOnlyWhatWasNamed() = runTest {
        // Участники бывают у опубликованной: у локальной их по определению один (инвариант MedKit).
        medKits.save(medKit().publish(), syncedAt = at)
        medKits.applyServerParticipants(HOME_KIT, participantCount = 3, syncedAt = at)

        assertTrue(medKits.describe(HOME_KIT, "Домашняя аптечка", "Верхний ящик"))

        val described = requireNotNull(medKits.find(HOME_KIT))
        assertEquals("Домашняя аптечка", described.name)
        assertEquals("Верхний ящик", described.location)
        assertEquals(3L, described.participantCount)
        assertEquals(MedKit.Publication.PUBLISHED, described.publication)
        assertEquals(at, medKits.observeSyncedAt(HOME_KIT).first())
    }

    /** Аптечки больше нет — писать переход некуда, и это ответ, а не сбой. */
    @Test
    fun describingAMedKitThatIsGoneWritesNothing() = runTest {
        val gone = Uuid.parse("00000000-0000-4000-8000-0000000000aa")

        assertFalse(medKits.describe(gone, "Неважно", null))
    }

    /** Снимок, называющий другую аптечку, откатывает и переключение: половины передачи не бывает. */
    @Test
    fun aSnapshotOfAnotherKitLeavesTheKitLocal() = runTest {
        val failure = runCatching {
            medKits.published(medKit().publish(), listOf(snapshot(medKitId = SHARED_KIT)), at)
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(MedKit.Publication.LOCAL, requireNotNull(medKits.find(HOME_KIT)).publication)
        assertEquals(tablets("20"), requireNotNull(database.packageRepository().find(PACK)).quantity)
    }
}
