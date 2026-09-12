package com.kert0n.medapp.feature.medkits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.medkit.MedKitFormError
import com.kert0n.medapp.presentation.medkit.MedKitFormPresentationDTO
import com.kert0n.medapp.presentation.medkit.toDomain
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Заведение и правка аптечки (PLAN H3 №3). Несохранённый ввод живёт здесь, а не в базе: пока
 * человек печатает, аптечки ещё нет, и отменённая форма не оставляет следов (PLAN F5).
 *
 * Правка не перезаписывает аптечку целиком: `describe` меняет название и место хранения, а
 * публикация, число участников и момент сверки остаются нынешними.
 */
@HiltViewModel
class MedKitFormViewModel @Inject constructor(
    private val medKits: MedKitStorageRepository,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(State())

    val state: StateFlow<State> = _state.asStateFlow()

    /** Открытие формы: пустая для новой аптечки, заполненная нынешними сведениями — для правки. */
    fun open(medKitId: Uuid?) {
        if (_state.value.opened) return
        _state.update { it.copy(medKitId = medKitId, opened = true) }
        if (medKitId == null) return
        viewModelScope.launch {
            val stored = medKits.find(medKitId) ?: return@launch
            _state.update {
                it.copy(form = MedKitFormPresentationDTO(stored.name, stored.location.orEmpty()))
            }
        }
    }

    fun edit(form: MedKitFormPresentationDTO) = _state.update { it.copy(form = form, error = null) }

    /** [onSaved] зовётся только когда записано: неверный ввод формы — не запись, а подсветка поля. */
    fun save(onSaved: () -> Unit) {
        val current = _state.value
        when (val described = current.form.toDomain()) {
            is ParsedInput.Rejected -> _state.update { it.copy(error = described.error) }
            is ParsedInput.Parsed -> viewModelScope.launch {
                val (name, location) = described.value
                if (current.medKitId == null) {
                    medKits.save(
                        MedKit(
                            id = Uuid.random(),
                            name = name,
                            location = location,
                            publication = MedKit.Publication.LOCAL,
                            participantCount = 1,
                            createdAt = clock.instant()
                        )
                    )
                } else {
                    medKits.describe(current.medKitId, name, location)
                }
                onSaved()
            }
        }
    }

    /** Что видит форма: напечатанное, причина отказа и то, правится ли уже заведённая аптечка. */
    data class State(
        val medKitId: Uuid? = null,
        val form: MedKitFormPresentationDTO = MedKitFormPresentationDTO(),
        val error: MedKitFormError? = null,
        val opened: Boolean = false
    ) {
        val isEditing: Boolean get() = medKitId != null
    }
}
