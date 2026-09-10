package com.kert0n.medapp.network.value

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit

/** Запись словаря с провода становится доменной единицей: имя проверяет сама величина. */
fun VocabularyEntryNetworkDTO.toQuantityUnit(): QuantityUnit = QuantityUnit(id = id, name = name)

/** Запись словаря с провода становится доменной формой выпуска. */
fun VocabularyEntryNetworkDTO.toDosageForm(): DosageForm = DosageForm(id = id, name = name)
