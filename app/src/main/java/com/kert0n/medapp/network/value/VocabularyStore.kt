package com.kert0n.medapp.network.value

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary

/**
 * Где лежит снимок словаря на устройстве. Интерфейс здесь, а реализация в хранении: резолвер
 * живёт в сети и о Room не знает, а хранение сеть видит (PLAN H1).
 */
interface VocabularyStore {

    suspend fun snapshot(): Vocabulary

    /** Записи переименовываются и добавляются, но не удаляются: словарь не убывает. */
    suspend fun save(units: List<QuantityUnit>, forms: List<DosageForm>)
}
