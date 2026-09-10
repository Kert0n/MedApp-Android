package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import kotlinx.coroutines.flow.Flow

/**
 * Хранение словарей единиц и форм. Сразу после установки в них лежит встроенный снимок, а
 * обновление с сервера переписывает его поверх, ничего не удаляя.
 */
interface VocabularyStorageRepository {

    fun observeUnits(): Flow<List<QuantityUnit>>

    fun observeForms(): Flow<List<DosageForm>>

    suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>)
}
