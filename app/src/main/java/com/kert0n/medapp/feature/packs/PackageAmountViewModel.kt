package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.pack.CountedAmount
import com.kert0n.medapp.presentation.pack.PackageAmountError
import com.kert0n.medapp.presentation.pack.PackageAmountPresentationDTO
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toDomain
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.storage.value.VocabularyStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Пересчёт и утилизация (PLAN H3 №9). Два разных действия на одном экране, потому что человек
 * приходит сюда с одним вопросом — «в коробке не столько, сколько записано», — а отвечает на него
 * по-разному: пересчитал или выбросил. Оба оставляют след в истории, и без следа лекарство
 * просто исчезло бы (PLAN D7).
 *
 * Уходящее в ноль спрашивается отдельно: пачка уйдёт в архив, и это решение человека, а не
 * побочный итог арифметики (PLAN H3 — подтверждения опасных действий).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PackageAmountViewModel @Inject constructor(
    private val packages: PackageStorageRepository,
    private val adjusting: PackageAdjusting,
    private val vocabulary: VocabularyStorageRepository
) : ViewModel() {

    private val packageId = MutableStateFlow<Uuid?>(null)

    private val editing = MutableStateFlow(Editing())

    val state: StateFlow<State> = combine(
        packageId.filterNotNull().flatMapLatest { packages.observe(it) },
        editing
    ) { projection, editing ->
        State(
            pkg = projection?.toPresentationDTO(),
            // Остаток держится величиной, а не строкой: «выйдет ли ноль» — вопрос к количеству,
            // и отвечает на него оно само (PLAN D1).
            quantity = projection?.quantity,
            gone = projection == null,
            tab = editing.tab,
            form = editing.form,
            error = editing.error,
            confirming = editing.confirming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun open(id: Uuid) {
        packageId.value = id
    }

    fun pick(tab: Tab) = editing.update { it.copy(tab = tab, error = null, confirming = false) }

    fun edit(form: PackageAmountPresentationDTO) =
        editing.update { it.copy(form = form, error = null) }

    /** Отказ от подтверждения ничего не пишет: человек передумал, а не подтвердил молчанием. */
    fun dismiss() = editing.update { it.copy(confirming = false) }

    /**
     * Записать. Уходящее в ноль сначала спрашивается, а уже подтверждённое пишется: [confirm]
     * зовётся из диалога.
     */
    fun submit(onWritten: () -> Unit) {
        viewModelScope.launch {
            val current = state.value
            val counted = parse(current) ?: return@launch
            if (current.leavesNothing(counted.amount)) editing.update { it.copy(confirming = true) }
            else write(current.tab, counted, onWritten)
        }
    }

    /** Подтверждённое пишется как есть: разбор тот же, а вопрос уже задан. */
    fun confirm(onWritten: () -> Unit) {
        viewModelScope.launch {
            val current = state.value
            val counted = parse(current) ?: return@launch
            editing.update { it.copy(confirming = false) }
            write(current.tab, counted, onWritten)
        }
    }

    private suspend fun parse(current: State): CountedAmount? {
        val unit = current.quantity?.unit ?: return null
        val counted = when (val parsed = current.form.toDomain(vocabulary.snapshot(), unit)) {
            is ParsedInput.Rejected -> {
                editing.update { it.copy(error = parsed.error) }
                return null
            }
            is ParsedInput.Parsed -> parsed.value
        }
        // Выбросить нисколько — не событие: записывать в историю нечего.
        if (current.tab == Tab.DISPOSAL && counted.amount.isZero) {
            editing.update { it.copy(error = PackageAmountError.NothingToDispose) }
            return null
        }
        return counted
    }

    private suspend fun write(tab: Tab, counted: CountedAmount, onWritten: () -> Unit) {
        val id = packageId.value ?: return
        val written = when (tab) {
            Tab.RECOUNT -> adjusting.recount(id, counted.amount, counted.note)
            Tab.DISPOSAL -> adjusting.dispose(id, counted.amount, state.value.form.reason, counted.note)
        }
        if (written) onWritten()
    }

    /** Что человек делает с количеством: пересчитал или выбросил. */
    enum class Tab { RECOUNT, DISPOSAL }

    data class State(
        val pkg: PackagePresentationDTO? = null,
        val quantity: Quantity? = null,
        val tab: Tab = Tab.RECOUNT,
        val form: PackageAmountPresentationDTO = PackageAmountPresentationDTO(),
        val error: PackageAmountError? = null,
        val confirming: Boolean = false,
        val gone: Boolean = false
    ) {
        /** Останется ли что-нибудь: пересчёт называет остаток целиком, утилизация — убыль. */
        fun leavesNothing(counted: Quantity): Boolean = when (tab) {
            Tab.RECOUNT -> counted.isZero
            Tab.DISPOSAL -> quantity?.minusOrZero(counted)?.isZero == true
        }
    }

    private data class Editing(
        val tab: Tab = Tab.RECOUNT,
        val form: PackageAmountPresentationDTO = PackageAmountPresentationDTO(),
        val error: PackageAmountError? = null,
        val confirming: Boolean = false
    )
}
