package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.medkit.toPresentationDTO
import com.kert0n.medapp.presentation.pack.PackageFormError
import com.kert0n.medapp.presentation.pack.PackageFormPresentationDTO
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toDomain
import com.kert0n.medapp.presentation.pack.toFacts
import com.kert0n.medapp.presentation.pack.toFormPresentationDTO
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.presentation.value.FormPresentationDTO
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import com.kert0n.medapp.presentation.value.toPresentationDTO
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.value.VocabularyStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Заведение и правка упаковки (PLAN H3 №7 и №8) — одна форма с двумя состояниями: у обеих те же
 * поля, и разошлись бы они при первой же правке одной из них.
 *
 * Несохранённый ввод живёт здесь, а не в базе: пока человек печатает, пачки ещё нет, и
 * отменённая форма не оставляет следов (PLAN F5).
 *
 * **Правка не трогает количество**: его меняют пересчёт и утилизация, и только они оставляют
 * след в истории (D7). Единица в правке тоже не меняется — смена единицы это отдельный сценарий
 * (D3), поэтому доза-подсказка меряется той единицей, что у пачки уже есть.
 *
 * Списки, из которых выбирают, приходят потоками: аптечка, заведённая в соседнем экране, и
 * единица, приехавшая с обновлением словаря, появляются в форме сами.
 */
@HiltViewModel
class PackageFormViewModel @Inject constructor(
    private val creation: PackageCreation,
    private val packages: PackageStorageRepository,
    private val vocabulary: VocabularyStorageRepository,
    medKits: MedKitStorageRepository,
    clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(State())

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            medKits.observeAll(LocalDate.now(clock)).collect { kits ->
                _state.update { it.copy(medKits = kits.map { kit -> kit.toPresentationDTO() }) }
            }
        }
        viewModelScope.launch {
            vocabulary.observeUnits().collect { units ->
                _state.update { it.copy(units = units.map { unit -> unit.toPresentationDTO() }) }
            }
        }
        viewModelScope.launch {
            vocabulary.observeForms().collect { forms ->
                _state.update { it.copy(forms = forms.map { form -> form.toPresentationDTO() }) }
            }
        }
    }

    /**
     * Открытие формы: для новой пачки аптечка подставлена той, из которой человек пришёл; для
     * правки — нынешние сведения пачки, а не пустые поля.
     */
    fun open(medKitId: Uuid?, packageId: Uuid? = null) {
        if (_state.value.opened) return
        _state.update {
            it.copy(form = it.form.copy(medKitId = medKitId), packageId = packageId, opened = true)
        }
        if (packageId == null) return
        viewModelScope.launch {
            val stored = packages.observe(packageId).first() ?: return@launch
            val described = stored.toPresentationDTO()
            _state.update {
                it.copy(form = described.toFormPresentationDTO(), stored = described)
            }
        }
    }

    fun edit(form: PackageFormPresentationDTO) =
        _state.update { it.copy(form = form, error = null) }

    /** Раскрытые необязательные поля — состояние экрана: заполнять их сразу человек не обязан. */
    fun toggleOptional() = _state.update { it.copy(optionalShown = !it.optionalShown) }

    /**
     * [onSaved] зовётся только когда записано: неверный ввод — не запись, а подсветка поля.
     * Ушедшая аптечка — тоже отказ, и он виден на месте выбора аптечки.
     */
    fun save(onSaved: (Uuid) -> Unit) {
        val current = _state.value
        val packageId = current.packageId
        if (packageId != null) return describe(packageId, current.form, onSaved)
        val form = current.form
        viewModelScope.launch {
            when (val described = form.toDomain(vocabulary.snapshot())) {
                // Отказ в скрытом поле бесполезен, пока поля не видно: раздел раскрывается сам.
                is ParsedInput.Rejected -> _state.update {
                    it.copy(
                        error = described.error,
                        optionalShown = it.optionalShown || !described.error.field.isRequired
                    )
                }
                is ParsedInput.Parsed -> {
                    val created = creation.create(
                        medKitId = described.value.medKitId,
                        facts = described.value.facts,
                        amount = described.value.amount
                    )
                    if (created == null) {
                        _state.update { it.copy(error = PackageFormError.MedKitMissing) }
                    } else {
                        onSaved(created)
                    }
                }
            }
        }
    }

    /**
     * Правка описания и только его: остаток, обвязка синхронизации и брони не трогаются, а
     * переход применяется к тому, что лежит в базе (PLAN F5).
     */
    private fun describe(packageId: Uuid, form: PackageFormPresentationDTO, onSaved: (Uuid) -> Unit) {
        viewModelScope.launch {
            val words = vocabulary.snapshot()
            val chosen = form.unit?.id?.let(words::unit)
            if (chosen == null) {
                _state.update { it.copy(error = PackageFormError.UnitMissing) }
                return@launch
            }
            when (val described = form.toFacts(words, chosen)) {
                is ParsedInput.Rejected -> _state.update {
                    it.copy(
                        error = described.error,
                        optionalShown = it.optionalShown || !described.error.field.isRequired
                    )
                }
                is ParsedInput.Parsed ->
                    if (packages.describe(packageId, described.value)) onSaved(packageId)
                    else _state.update { it.copy(gone = true) }
            }
        }
    }

    /** Что видит форма: напечатанное, причина отказа и списки, из которых выбирают. */
    data class State(
        val form: PackageFormPresentationDTO = PackageFormPresentationDTO(),
        val error: PackageFormError? = null,
        val medKits: List<MedKitPresentationDTO> = emptyList(),
        val units: List<UnitPresentationDTO> = emptyList(),
        val forms: List<FormPresentationDTO> = emptyList(),
        val packageId: Uuid? = null,
        /** Что о пачке уже записано: в правке отсюда берётся количество, которое форма не трогает. */
        val stored: PackagePresentationDTO? = null,
        val optionalShown: Boolean = false,
        val opened: Boolean = false,
        val gone: Boolean = false
    ) {
        val isEditing: Boolean get() = packageId != null
    }
}
