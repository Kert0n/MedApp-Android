package com.kert0n.medapp.storage.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLETS_ID
import com.kert0n.medapp.fixture.fileDatabase
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Схема переживает обновление приложения. Экспорт схемы — необходимое, но не достаточное:
 * проверка открывает базу объявленной версии и сверяет её со скомпилированной (PLAN F4).
 *
 * Переход 1→2 — колонка `outcome_unknown` у очереди (PLAN E3, F4). Переход 2→3 — движение стало
 * записью о пачке: колонки аптечек ушли вместе с переносами (D7). База прошлой версии переезжает
 * целиком, а не заводится заново.
 */
class MedAppDatabaseMigrationTest {

    private val name = "migration.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MedAppDatabase::class.java
    )

    /** Экспортированная схема совпадает со скомпилированной: расхождение падает здесь. */
    @Test
    fun exportedSchemaMatchesTheCompiledOne() {
        helper.createDatabase(name, MedAppDatabase.VERSION).close()
        helper.runMigrationsAndValidate(name, MedAppDatabase.VERSION, true).close()
    }

    /** Очередь версии 1 переезжает в версию 2 вместе со строкой; исход старой отправки — известен. */
    @Test
    fun queueOfVersionOneSurvivesTheMoveToVersionTwo() {
        val file = "migrate-1-2.db"
        helper.createDatabase(file, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO sync_operations (id, kind, payload, payload_version, sequence, status, attempts, created_at) " +
                    "VALUES ('00000000-0000-4000-8000-000000000091', 'DELETE', '{\"packageId\":\"$PACK\"}', 1, 0, 'SENDING', 2, 0)"
            )
        }

        val v2 = helper.runMigrationsAndValidate(file, 2, true, MedAppDatabase.MIGRATION_1_2)
        val row = v2.query("SELECT status, attempts, outcome_unknown FROM sync_operations")
        row.moveToFirst()
        assertEquals("SENDING", row.getString(0))
        assertEquals(2, row.getInt(1))
        assertEquals(0, row.getInt(2))
        row.close()
        v2.close()
    }

    /**
     * Движение стало записью о пачке: колонки аптечек ушли, а переносы вместе с ними — они
     * говорили только о местах (PLAN D7). Остальная история переезжает целиком.
     */
    @Test
    fun movementsOfVersionTwoLoseTheirMedKitsAndKeepTheirHistory() {
        val file = "migrate-2-3.db"
        val receipt = "00000000-0000-4000-8000-000000000101"
        val transfer = "00000000-0000-4000-8000-000000000102"
        helper.createDatabase(file, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO stock_adjustments " +
                    "(id, package_id, kind, unit_id, observed_at, occurred_at, amount, med_kit_id) " +
                    "VALUES ('$receipt', '$PACK', 'RECEIPT', '$TABLETS_ID', 10, 10, '20', '$HOME_KIT')"
            )
            v2.execSQL(
                "INSERT INTO stock_adjustments " +
                    "(id, package_id, kind, unit_id, observed_at, occurred_at, amount, " +
                    "source_med_kit_id, target_med_kit_id) " +
                    "VALUES ('$transfer', '$PACK', 'TRANSFER', '$TABLETS_ID', 20, 20, '20', " +
                    "'$HOME_KIT', '$SHARED_KIT')"
            )
        }

        val v3 = helper.runMigrationsAndValidate(file, 3, true, MedAppDatabase.MIGRATION_2_3)

        val kinds = v3.query("SELECT id, kind FROM stock_adjustments ORDER BY observed_at")
        val survived = buildList {
            while (kinds.moveToNext()) add(kinds.getString(0) to kinds.getString(1))
        }
        kinds.close()
        v3.close()

        assertEquals(listOf(receipt to "RECEIPT"), survived)
    }

    /**
     * Разрушающий откат не включён. Файл более новой версии не стирается молча — иначе
     * обновление приложения теряло бы очередь и историю (PLAN F4).
     */
    @Test
    fun aNewerDatabaseFileIsRefusedInsteadOfWiped() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "refuses-downgrade.db"

        // Засеянная база: пачка не пишется без аптечки и словаря, ключи это держат (F2).
        val written = fileDatabase(file)
        val paracetamol = pack(quantity = tablets("20"))
        written.packages().save(paracetamol.toStorageEntity(), paracetamol.toDetailsStorageEntity())
        written.openHelper.writableDatabase.execSQL("PRAGMA user_version = 99")
        written.close()

        val reopened = Room.databaseBuilder(context, MedAppDatabase::class.java, file).build()
        val refusal = runCatching { reopened.packages().find(PACK) }.exceptionOrNull()
        reopened.close()

        assertNotNull("более новая база открылась как ни в чём не бывало", refusal)
        assertEquals(IllegalStateException::class, refusal!!::class)

        // Отказ не должен быть уничтожением: строка на месте, её просто не отдали.
        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(file).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        val left = raw.rawQuery("SELECT COUNT(*) FROM packages", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        raw.close()
        context.deleteDatabase(file)

        assertEquals("данные стёрлись вместо отказа открыть базу", 1, left)
    }
}
