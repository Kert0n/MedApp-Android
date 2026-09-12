package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.medkit.toPresentationDTO
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageQuery
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Содержимое аптечки (PLAN H3 №4) и все лекарства сразу (№5) — один экран с двумя областями
 * поиска: аптечка названа или не названа (`PackageQuery.medKitId`). Двух списков не заводится:
 * читают они одно и то же, и разошлись бы при первой же правке одного из них.
 *
 * Запрос — три независимых поля, а не история нажатий: конвейер один, и порядок нажатий
 * результат не меняет (PLAN H4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedKitContentsViewModel @Inject constructor(
    private val packages: PackageStorageRepository,
    private val medKits: MedKitStorageRepository,
    private val clock: Clock
) : ViewModel() {

    /** `null` — экран ещё не открыт: до этого неизвестно даже, чью аптечку читать. */
    private val request = MutableStateFlow<PackageQuery?>(null)

    val state: StateFlow<State> = request.filterNotNull()
        .flatMapLatest { query ->
            val medKit = query.medKitId?.let { medKits.observe(it) } ?: flowOf(null)
            combine(medKit, packages.list(query, LocalDate.now(clock))) { kit, packs ->
                State(
                    medKit = kit?.toPresentationDTO(),
                    packs = packs.map { it.toPresentationDTO() },
                    query = query
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** [medKitId] `null` — ищем по всем доступным аптечкам (экран 5). */
    fun open(medKitId: Uuid?) {
        if (request.value != null) return
        request.value = PackageQuery(medKitId = medKitId)
    }

    /**
     * Что показывает экран. `packs == null` — чтение ещё не пришло: показывать «пусто» рано,
     * это было бы неправдой.
     */
    data class State(
        val medKit: MedKitPresentationDTO? = null,
        val packs: List<PackagePresentationDTO>? = null,
        val query: PackageQuery = PackageQuery()
    )
}
