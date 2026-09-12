package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.medkit.toPresentationDTO
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.presentation.value.FormPresentationDTO
import com.kert0n.medapp.presentation.value.toPresentationDTO
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Содержимое аптечки (PLAN H3 №4) и все лекарства сразу (№5) — один экран с двумя областями
 * поиска: аптечка названа или не названа (`PackageQuery.medKitId`). Двух списков не заводится:
 * читают они одно и то же, и разошлись бы при первой же правке одного из них.
 *
 * **Порядок нажатий результат не меняет.** Запрос — три независимых поля, а не история действий:
 * искать и потом фильтровать это то же самое, что фильтровать и потом искать. Конвейер один и
 * живёт в запросе к базе: аптечки, поиск, фильтр, просроченные вперёд, сортировка (PLAN H4).
 *
 * Из чего выбирать категорию и форму, знает сама область, а не выбранное в ней: список этих
 * значений читается без фильтра, иначе выбранная категория осталась бы единственной, и вернуться
 * к другой было бы нечем.
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

    private val today: LocalDate get() = LocalDate.now(clock)

    val state: StateFlow<State> = request.filterNotNull()
        .flatMapLatest { query ->
            combine(
                medKits.observeAll(today),
                packages.list(query, today),
                choicesOf(query.medKitId)
            ) { kits, packs, choices ->
                State(
                    medKit = kits.firstOrNull { it.id == query.medKitId }?.toPresentationDTO(),
                    everywhere = query.medKitId == null,
                    packs = packs.map { it.toPresentationDTO() },
                    // Из какой аптечки пачка, нужно только там, где их много (экран 5).
                    medKitNames = if (query.medKitId != null) emptyMap()
                    else kits.associate { it.id to it.name },
                    categories = choices.categories,
                    forms = choices.forms,
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

    fun search(text: String) = request.update { it?.copy(text = text) }

    /** Фильтр ровно один: два одновременных сузили бы список до пустого чаще, чем помогли (H4). */
    fun filter(filter: PackageQuery.Filter?) = request.update { it?.copy(filter = filter) }

    fun sort(sort: PackageQuery.Sort) = request.update { it?.copy(sort = sort) }

    /** Сбросить: запрос ни при чём, если человек просто не нашёл нужного. */
    fun reset() = request.update { it?.let { query -> PackageQuery(medKitId = query.medKitId) } }

    /**
     * Что вообще есть в этой области — из чего предлагать категорию и форму. Читается без поиска
     * и фильтра, поэтому набор не схлопывается вслед за выбором.
     */
    private fun choicesOf(medKitId: Uuid?) =
        packages.list(PackageQuery(medKitId = medKitId), today).map { packs ->
            Choices(
                categories = packs.mapNotNull { it.facts.category }.distinct().sorted(),
                forms = packs.mapNotNull { it.facts.form }
                    .distinctBy { it.id }
                    .map { it.toPresentationDTO() }
                    .sortedBy { it.name }
            )
        }

    /**
     * Что показывает экран. `packs == null` — чтение ещё не пришло: показывать «пусто» рано, это
     * было бы неправдой. [everywhere] — экран 5: аптечка не названа, и у строк видно, чья пачка.
     */
    data class State(
        val medKit: MedKitPresentationDTO? = null,
        val everywhere: Boolean = false,
        val packs: List<PackagePresentationDTO>? = null,
        val medKitNames: Map<Uuid, String> = emptyMap(),
        val categories: List<String> = emptyList(),
        val forms: List<FormPresentationDTO> = emptyList(),
        val query: PackageQuery = PackageQuery()
    ) {
        /** Ищут или сужают — значит пустота значит «не нашлось», а не «здесь ничего нет». */
        val isNarrowed: Boolean get() = query.searchText.isNotEmpty() || query.filter != null
    }

    private data class Choices(
        val categories: List<String>,
        val forms: List<FormPresentationDTO>
    )
}
