package com.kert0n.medapp.storage.value

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Query("SELECT * FROM quantity_units ORDER BY name")
    fun observeUnits(): Flow<List<QuantityUnitStorageEntity>>

    @Query("SELECT * FROM form_types ORDER BY name")
    fun observeForms(): Flow<List<DosageFormStorageEntity>>

    /**
     * Обновление обоих словарей одной транзакцией: записи переименовываются и добавляются, но
     * не удаляются — на снятую с сервера единицу ещё могут ссылаться пачки.
     */
    @Transaction
    suspend fun save(units: List<QuantityUnitStorageEntity>, forms: List<DosageFormStorageEntity>) {
        upsertUnits(units)
        upsertForms(forms)
    }

    @Upsert
    suspend fun upsertUnits(units: List<QuantityUnitStorageEntity>)

    @Upsert
    suspend fun upsertForms(forms: List<DosageFormStorageEntity>)
}
