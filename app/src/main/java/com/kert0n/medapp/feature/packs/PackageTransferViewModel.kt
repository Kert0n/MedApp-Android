package com.kert0n.medapp.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kert0n.medapp.domain.medkit.MedKit
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Перенос упаковки (PLAN H3 №11): человек переложил коробку с дачи домой, и учёт должен узнать об
 * этом. Место меняется, остаток — нет, а в истории остаётся запись с двумя концами (PLAN D7).
 *
 * Предлагаются только **местные** аптечки, кроме нынешней: перенос в общую требует связи и
 * согласия на публикацию (PLAN C3, E6). Если общие есть, экран говорит об этом строкой, а не
 * молча прячет их из списка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PackageTransferViewModel @Inject constructor(
    private val packages: PackageStorageRepository,
    private val adjusting: PackageAdjusting,
    medKits: MedKitStorageRepository,
    clock: Clock
) : ViewModel() {

    private val packageId = MutableStateFlow<Uuid?>(null)

    private val chosen = MutableStateFlow<Uuid?>(null)

    val state: StateFlow<State> = combine(
        packageId.filterNotNull().flatMapLatest { packages.observe(it) },
        medKits.observeAll(LocalDate.now(clock)),
        chosen
    ) { projection, kits, chosen ->
        val here = projection?.medKit?.id
        State(
            pkg = projection?.toPresentationDTO(),
            gone = projection == null,
            candidates = kits
                .filter { it.id != here && it.publication == MedKit.Publication.LOCAL }
                .map { it.toPresentationDTO() },
            // Общие не предлагаются, и человек должен узнать почему, а не искать их глазами.
            hasSharedOnes = kits.any { it.id != here && it.publication != MedKit.Publication.LOCAL },
            chosen = chosen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun open(id: Uuid) {
        packageId.value = id
    }

    fun choose(medKitId: Uuid) {
        chosen.value = medKitId
    }

    /** [onMoved] зовётся только когда записано: некуда или нечего переносить — не перенос. */
    fun transfer(onMoved: () -> Unit) {
        val id = packageId.value ?: return
        val target = chosen.value ?: return
        viewModelScope.launch { if (adjusting.moveTo(id, target)) onMoved() }
    }

    data class State(
        val pkg: PackagePresentationDTO? = null,
        val candidates: List<MedKitPresentationDTO> = emptyList(),
        val hasSharedOnes: Boolean = false,
        val chosen: Uuid? = null,
        val gone: Boolean = false
    )
}
