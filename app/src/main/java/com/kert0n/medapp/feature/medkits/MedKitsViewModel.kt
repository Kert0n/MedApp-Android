package com.kert0n.medapp.feature.medkits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.medkit.toPresentationDTO
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Список аптечек. Сохранённое приходит потоком из Room, поэтому «обновить экран после записи»
 * руками не нужно нигде: завели аптечку — список перерисовался сам (PLAN H1).
 *
 * Загрузки как состояния здесь нет: чтение из базы не ходит в сеть, а пустой список — это не
 * ожидание, а ответ «пока ничего не заведено».
 */
@HiltViewModel
class MedKitsViewModel @Inject constructor(medKits: MedKitStorageRepository) : ViewModel() {

    val state: StateFlow<List<MedKitPresentationDTO>?> = medKits.observeAll()
        .map { kits -> kits.map { it.toPresentationDTO() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
