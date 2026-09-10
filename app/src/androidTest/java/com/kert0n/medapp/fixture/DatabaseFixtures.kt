package com.kert0n.medapp.fixture

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.storage.database.MedAppDatabase

/**
 * База для проверки в памяти: прогон не оставляет файла и не зависит от прошлого прогона.
 * Ограничения внешних ключей включены явно — без них `RESTRICT` не проверяется вовсе.
 */
fun inMemoryDatabase(): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.inMemoryDatabaseBuilder(context, MedAppDatabase::class.java).build()
}

/**
 * База в файле: нужна там, где проверяется, что записанное переживает закрытие соединения —
 * в памяти это не проверить по определению.
 */
fun fileDatabase(name: String): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.deleteDatabase(name)
    return Room.databaseBuilder(context, MedAppDatabase::class.java, name).build()
}

fun reopenFileDatabase(name: String): MedAppDatabase {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return Room.databaseBuilder(context, MedAppDatabase::class.java, name).build()
}
