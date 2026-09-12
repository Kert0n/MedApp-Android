package com.kert0n.medapp.feature.packages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.course.CourseDraft
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.Scenarios
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.courseRecord
import com.kert0n.medapp.fixture.courseRepository
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.packageRepository
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.queue.StoredSyncOperation
import com.kert0n.medapp.queue.SyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Человек переставил коробку на другую полку (PLAN E6): та же коробка, курс её не теряет, следа
 * в истории нет (D7). Что ещё нужно серверу, решает граница публикации — четыре случая.
 */
@RunWith(AndroidJUnit4::class)
class PackageRelocationTest {

    private lateinit var database: MedAppDatabase
    private lateinit var relocation: PackageRelocation

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        relocation = Scenarios(database, LATER).packageRelocation
        database.packageRepository().add(pack(id = PACK, quantity = tablets("20"), form = TABLET_FORM))
        val plan = activeCourse(sources = listOf(source(PACK, 5)))
        database.courseRepository().activate(CourseDraft.Activation(plan, courseRecord(prescription = plan.prescription)))
    }

    @After
    fun tearDown() = database.close()

    private suspend fun publish(vararg kits: Uuid) {
        for (kit in kits) {
            database.medKits().upsert(
                medKit(id = kit, publication = MedKit.Publication.PUBLISHED, participantCount = 2).toMedKitStorageEntity()
            )
        }
    }

    private suspend fun commands(): List<SyncCommand> = database.syncOperations().all()
        .map { (it.toDomain(VOCABULARY) as StoredSyncOperation.Readable).operation.command }

    private suspend fun assertMovedAndStillASource() {
        assertEquals(SHARED_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(listOf(PACK), database.courses().sourcePackagesOf(COURSE))
        assertTrue(database.stockMovements().ofPackage(PACK).isEmpty())
    }

    @Test
    fun localToLocalChangesOnlyThePlace() = runTest {
        assertEquals(PackageRelocation.Outcome.MOVED, relocation.move(PACK, SHARED_KIT))

        assertMovedAndStillASource()
        assertEquals(emptyList<SyncCommand>(), commands())
    }

    @Test
    fun sharedToSharedTellsTheServerToMove() = runTest {
        publish(HOME_KIT, SHARED_KIT)

        assertEquals(PackageRelocation.Outcome.MOVED, relocation.move(PACK, SHARED_KIT))

        assertMovedAndStillASource()
        assertEquals(listOf(PackageSyncCommand.Move(PACK, SHARED_KIT)), commands())
    }

    /** Местная коробка на общей полке — публикация коробки, а выделение курса едет следом бронью. */
    @Test
    fun localToSharedPublishesThePackageWithItsClaim() = runTest {
        publish(SHARED_KIT)

        assertEquals(PackageRelocation.Outcome.MOVED, relocation.move(PACK, SHARED_KIT))

        assertMovedAndStillASource()
        val queued = commands()
        val create = queued[0] as PackageSyncCommand.Create
        assertEquals(PACK, create.packageId)
        assertEquals(SHARED_KIT, create.medKitId)
        assertEquals(tablets("20"), create.quantity)
        val claim = queued[1] as PackageSyncCommand.SetClaim
        assertEquals(tablets("10"), claim.amount)
        assertEquals(2, queued.size)
        // Обвязки у только что опубликованной коробки ещё нет: первое подтверждённое число даст ответ.
        assertNull(requireNotNull(database.packages().find(PACK)).pack.syncState().version)
    }

    /** Без выделения бронь не ставится: нулевая бронь — это снятие, а снимать нечего. */
    @Test
    fun localToSharedWithoutACourseSendsOnlyTheCreation() = runTest {
        publish(SHARED_KIT)
        database.courseRepository().close(
            requireNotNull(database.courseRepository().findRecord(COURSE))
                .close(com.kert0n.medapp.domain.course.CourseRecord.Outcome.CANCELLED, LATER)
        )

        relocation.move(PACK, SHARED_KIT)

        assertEquals(1, commands().filterIsInstance<PackageSyncCommand.Create>().size)
        assertEquals(1, commands().size)
    }

    @Test
    fun sharedToLocalNeedsTheTargetPublishedFirst() = runTest {
        publish(HOME_KIT)

        assertEquals(PackageRelocation.Outcome.TARGET_NEEDS_PUBLICATION, relocation.move(PACK, SHARED_KIT))

        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
        assertEquals(emptyList<SyncCommand>(), commands())
    }

    @Test
    fun aMissingTargetOrTheSameShelfChangesNothing() = runTest {
        assertEquals(PackageRelocation.Outcome.TARGET_GONE, relocation.move(PACK, Uuid.random()))
        assertEquals(PackageRelocation.Outcome.TARGET_IS_THE_SAME, relocation.move(PACK, HOME_KIT))
        assertEquals(PackageRelocation.Outcome.GONE, relocation.move(Uuid.random(), SHARED_KIT))
        assertEquals(HOME_KIT, database.packageRepository().find(PACK)?.medKit?.id)
    }
}
