package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.medkit.toPresentationDTO
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Карточка упаковки (PLAN H3 №6): всё известное об этой коробке — сколько в ней есть, сколько из
 * этого могу взять я и сколько свободно любому (PLAN D4).
 *
 * Аптечка читается рядом: человеку нужно её **название**, а пачка держит ссылку и знает только
 * тождество.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PackageViewModel @Inject constructor(
    private val packages: PackageStorageRepository,
    private val medKits: MedKitStorageRepository,
    private val clock: Clock
) : ViewModel() {

    private val packageId = MutableStateFlow<Uuid?>(null)

    val state: StateFlow<State> = packageId.filterNotNull()
        .flatMapLatest { id -> packages.observe(id) }
        .flatMapLatest { projection ->
            if (projection == null) flowOf(State(gone = true))
            else medKits.observe(projection.medKit.id).map { kit ->
                State(
                    pkg = projection.toPresentationDTO(),
                    medKit = kit?.toPresentationDTO(),
                    today = LocalDate.now(clock)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun open(id: Uuid) {
        packageId.value = id
    }

    /**
     * Что показывает карточка. `pkg == null` и не [gone] — чтение ещё не пришло; [gone] — пачки
     * больше нет: её удалили вместе с аптечкой, пока человек на неё смотрел.
     */
    data class State(
        val pkg: PackagePresentationDTO? = null,
        val medKit: MedKitPresentationDTO? = null,
        // Пока пачка не прочитана, сегодня никому не нужно: просрочку считают по ней.
        val today: LocalDate = LocalDate.MIN,
        val gone: Boolean = false
    )
}
