package com.kert0n.medapp.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.storage.course.toSourceStorageEntities
import com.kert0n.medapp.storage.course.toStorageEntity as toCourseStorageEntity
import com.kert0n.medapp.storage.course.toTimeStorageEntities
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.fixture.save
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity
import com.kert0n.medapp.storage.value.toStorageEntity
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.kert0n.medapp.fixture.VOCABULARY

/**
 * Домен ссылается объектом, а строки — идентификаторами: сборка разрешает их связями, той же
 * транзакцией и по запросу на связь на всю выборку, а не по строке (PLAN F1).
 */
class ObjectReferencesTest {

    private lateinit var database: MedAppDatabase

    /** Все запросы к базе — чтобы посчитать, сколько раз читали `packages`. */
    private val queries = mutableListOf<String>()

    @Before
    fun openDatabase() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MedAppDatabase::class.java)
            .setQueryCallback({ sql, _ -> synchronized(queries) { queries += sql } }, Executors.newSingleThreadExecutor())
            .build()
        database.vocabulary().save(
            units = listOf(TABLETS, MILLILITRES).map { it.toStorageEntity() },
            forms = listOf(TABLET_FORM).map { it.toStorageEntity() }
        )
        database.medKits().upsert(medKit().toMedKitStorageEntity())
        database.medKits().upsert(medKit(id = SHARED_KIT, name = "Дача").toMedKitStorageEntity())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun packageCarriesItsMedKitAsARef() = runTest {
        val onTheDacha = pack(medKit = medKit(id = SHARED_KIT, name = "Дача").ref)
        database.packages().save(onTheDacha)

        val restored = requireNotNull(database.packages().find(PACK)).toDomain(VOCABULARY)
        assertEquals(SHARED_KIT, restored.medKit.id)
        assertEquals(MedKit.Publication.LOCAL, restored.medKit.publication)
    }

    @Test
    fun courseIsReadWithItsPackagesInOneTransaction() = runTest {
        for (pkg in listOf(pack(id = PACK), pack(id = OTHER_PACK, quantity = tablets("12")))) {
            database.packages().save(pkg)
        }
        val plan = activeCourse(sources = listOf(source(PACK, 5), source(OTHER_PACK, 4)))
        database.courses().saveCourse(
            plan.toCourseStorageEntity(),
            plan.schedule.toTimeStorageEntities(COURSE),
            plan.medicine.toSourceStorageEntities(COURSE)
        )

        val restored = requireNotNull(database.courses().findPlan(COURSE)).toPlan(VOCABULARY)
        assertEquals(listOf(PACK, OTHER_PACK), restored.sources.map { it.pkg.id })
        // Ссылка несёт то, что курсу нужно знать о пачке, — и не несёт ни остатка, ни места.
        assertEquals(TABLETS, restored.sources.last().pkg.unit)
        assertEquals("Парацетамол", restored.sources.first().pkg.name)
    }

    @Test
    fun aHundredMovementsLoadTheirPackagesInOneQuery() = runTest {
        val paracetamol = pack(id = PACK)
        database.packages().save(paracetamol)
        for (i in 0 until 100) {
            val movement = StockMovement.Recount(
                id = Uuid.parse("00000000-0000-4000-8000-%012x".format(0x1000 + i)),
                pkg = paracetamol.ref,
                before = tablets("20"),
                after = tablets("19"),
                occurredAt = Instant.EPOCH.plusSeconds(i.toLong()),
                observedAt = LATER.plusSeconds(i.toLong())
            )
            database.stockMovements().insert(movement.toMovementStorageEntity())
        }
        synchronized(queries) { queries.clear() }

        val history = database.stockMovements().ofPackage(PACK).map { it.toDomain(VOCABULARY) }

        assertEquals(100, history.size)
        assertTrue(history.all { it.pkg.id == PACK && it.pkg.name == "Парацетамол" })
        // Room грузит связь одним `IN`-запросом на всю выборку (и повторяет его для вложенных
        // связей пачки), а не по запросу на строку: сто строк — не сто чтений.
        val packageReads = synchronized(queries) {
            queries.filter { it.trimStart().startsWith("SELECT", ignoreCase = true) && it.contains("FROM `packages`") }
        }
        assertTrue("чтений packages: ${packageReads.size}", packageReads.size <= 2)
        assertTrue(packageReads.all { it.contains("WHERE `id` IN (") })
    }
}
