package com.kert0n.medapp.fixture

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.storage.database.MedAppDatabase
import com.kert0n.medapp.storage.value.toStorageEntity
import kotlinx.coroutines.runBlocking

/**
 * База для проверки в памяти: прогон не оставляет файла и не зависит от прошлого прогона.
 * Ограничения внешних ключей включены явно — без них `RESTRICT` не проверяется вовсе.
 *
 * Словарь засеян фикстурами: строки держат идентификаторы единиц и форм, а собираются в домен по
 * снимку словаря, и без него ни одна пачка из базы не читается.
 */
fun inMemoryDatabase(): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.inMemoryDatabaseBuilder(context, MedAppDatabase::class.java).build().seeded()
}

private fun MedAppDatabase.seeded(): MedAppDatabase = apply {
    runBlocking {
        vocabulary().save(
            units = listOf(TABLETS, MILLILITRES).map { it.toStorageEntity() },
            forms = listOf(TABLET_FORM, CAPSULE_FORM).map { it.toStorageEntity() }
        )
    }
}

/**
 * База в файле: нужна там, где проверяется, что записанное переживает закрытие соединения —
 * в памяти это не проверить по определению.
 */
fun fileDatabase(name: String): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.deleteDatabase(name)
    return Room.databaseBuilder(context, MedAppDatabase::class.java, name).build().seeded()
}

fun reopenFileDatabase(name: String): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.databaseBuilder(context, MedAppDatabase::class.java, name).build()
}

/**
 * Запись, которую схема запрещает: возвращает отказ базы, чтобы тест утверждал именно про него.
 *
 * Вложенный `runTest` внутри `runTest` не запускается, поэтому ожидание отказа выражается
 * перехватом, а не `assertThrows` вокруг второго построителя.
 */
suspend fun rejectedByDatabase(block: suspend () -> Unit): Throwable =
    runCatching { block() }.exceptionOrNull()
        ?: throw AssertionError("база приняла запись, которую схема запрещает")

/**
 * Репозитории поверх открытой базы. Транзакции F5 идут через несколько таблиц, поэтому у их
 * владельцев несколько DAO; собирать их в каждом тесте заново значило бы повторять граф руками.
 */
fun MedAppDatabase.packageRepository() = com.kert0n.medapp.storage.pack.PackageRoomRepository(
    this, packages(), courses(), stockMovements(), syncOperations(), vocabulary()
)

fun MedAppDatabase.courseRepository() = com.kert0n.medapp.storage.course.CourseRoomRepository(
    this, courses(), intakes(), syncOperations(), vocabulary()
)

fun MedAppDatabase.intakeRepository() = com.kert0n.medapp.storage.intake.IntakeRoomRepository(
    this, intakes(), packages(), courses(), syncOperations(), vocabulary()
)

fun MedAppDatabase.queueRepository() = com.kert0n.medapp.storage.server.SyncOperationRoomRepository(
    this, syncOperations(), packages(), intakes(), vocabulary()
)
