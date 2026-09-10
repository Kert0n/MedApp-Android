package com.kert0n.medapp.storage.database

import android.content.res.AssetManager
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.network.value.VocabularySnapshotNetworkDTO
import com.kert0n.medapp.network.value.toDosageForm
import com.kert0n.medapp.network.value.toQuantityUnit
import kotlin.uuid.Uuid

/**
 * Засев словарей при создании базы: встроенный снимок ложится в таблицы вместе со схемой, и
 * первый же экран видит единицы и формы. Обновление с сервера потом переписывает имена поверх.
 *
 * Пишется сырым SQL: DAO внутри создания базы открыл бы её повторно. Записи проходят через
 * доменные величины, поэтому неверное имя в снимке не попадает в базу молча.
 */
class BundledVocabulary(private val read: () -> String) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val snapshot = medAppJson.decodeFromString(VocabularySnapshotNetworkDTO.serializer(), read())
        insert(db, "quantity_units", snapshot.quantityUnits.map { it.toQuantityUnit().run { id to name } })
        insert(db, "form_types", snapshot.formTypes.map { it.toDosageForm().run { id to name } })
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, rows: List<Pair<Uuid, String>>) {
        for ((id, name) in rows) {
            db.execSQL("INSERT OR IGNORE INTO $table (id, name) VALUES (?, ?)", arrayOf(id.toString(), name))
        }
    }

    companion object {
        fun fromAssets(assets: AssetManager) = BundledVocabulary {
            assets.open(VocabularySnapshotNetworkDTO.ASSET).use { it.readBytes().decodeToString() }
        }
    }
}
