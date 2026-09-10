package com.kert0n.medapp.storage.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.fixture.PACK
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
 * Настоящего перехода N→N+1 здесь ещё нет — это первая версия, мигрировать не с чего. Стенд
 * заведён, чтобы такой тест появился вместе с первым реальным изменением схемы, а не после
 * него.
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

    /**
     * Разрушающий откат не включён. Файл более новой версии не стирается молча — иначе
     * обновление приложения теряло бы очередь и историю (PLAN F4).
     */
    @Test
    fun aNewerDatabaseFileIsRefusedInsteadOfWiped() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "refuses-downgrade.db"
        context.deleteDatabase(file)

        val written = Room.databaseBuilder(context, MedAppDatabase::class.java, file).build()
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
