package com.kert0n.medapp.feature.medkits

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitProjection
import com.kert0n.medapp.fixture.MainDispatcherRule
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.presentation.medkit.MedKitFormError
import com.kert0n.medapp.presentation.medkit.MedKitFormPresentationDTO
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Форма аптечки: пока человек печатает, аптечки ещё нет, и отменённая форма не оставляет следов
 * (PLAN F5). Правка меняет названные сведения, а не переписывает аптечку целиком.
 */
class MedKitFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")
    private val home: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000011")

    private class Kits(private val stored: MedKit? = null) : MedKitStorageRepository {
        val saved = mutableListOf<MedKit>()
        val described = mutableListOf<Triple<Uuid, String, String?>>()

        override fun observeAll(): Flow<List<MedKitProjection>> = flowOf(emptyList())
        override fun observe(id: Uuid): Flow<MedKitProjection?> = emptyFlow()
        override suspend fun find(id: Uuid): MedKit? = stored
        override fun observeSyncedAt(id: Uuid): Flow<Instant?> = emptyFlow()
        override suspend fun save(medKit: MedKit, syncedAt: Instant?) { saved += medKit }
        override suspend fun describe(id: Uuid, name: String, location: String?): Boolean {
            described += Triple(id, name, location)
            return true
        }
        override suspend fun applyServerParticipants(id: Uuid, participantCount: Long, syncedAt: Instant) = Unit
        override suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant) = Unit
    }

    private fun viewModel(kits: Kits) =
        MedKitFormViewModel(kits, Clock.fixed(now, ZoneOffset.UTC))

    private fun shared() = MedKit(
        id = home,
        name = "Домашняя",
        location = "Верхний ящик",
        publication = MedKit.Publication.PUBLISHED,
        participantCount = 3,
        createdAt = now.minusSeconds(600)
    )

    @Test
    fun aNewMedKitIsLocalAndHasOneParticipant() = runTest {
        val kits = Kits()
        val form = viewModel(kits)
        form.open(medKitId = null)
        form.edit(MedKitFormPresentationDTO(name = "Дача", location = "Полка"))

        var done = false
        form.save { done = true }

        assertTrue(done)
        val created = kits.saved.single()
        assertEquals("Дача", created.name)
        assertEquals("Полка", created.location)
        assertEquals(MedKit.Publication.LOCAL, created.publication)
        assertEquals(1L, created.participantCount)
    }

    /**
     * Правка идёт `describe`, а не полной записью: общая запись положила бы поверх нынешних
     * публикацию и число участников те, что экран прочитал когда-то раньше (PLAN F5).
     *
     * Красная проверка: сохранять правку через `save(medKit)` — аптечка из общей станет
     * локальной с одним участником.
     */
    @Test
    fun editingChangesOnlyWhatWasNamed() = runTest {
        val kits = Kits(stored = shared())
        val form = viewModel(kits)
        form.open(medKitId = home)
        form.edit(form.state.value.form.copy(name = "Домашняя аптечка"))

        form.save {}

        assertTrue("полной записи быть не должно", kits.saved.isEmpty())
        assertEquals(Triple(home, "Домашняя аптечка", "Верхний ящик"), kits.described.single())
    }

    /** Открытая на правку форма показывает нынешние сведения, а не пустые поля. */
    @Test
    fun anOpenedFormShowsWhatIsStored() = runTest {
        val form = viewModel(Kits(stored = shared()))

        form.open(medKitId = home)

        assertEquals("Домашняя", form.state.value.form.name)
        assertEquals("Верхний ящик", form.state.value.form.location)
        assertTrue(form.state.value.isEditing)
    }

    /**
     * Неверный ввод — не запись, а подсветка поля: ни аптечки, ни ухода с экрана.
     *
     * Красная проверка: писать без разбора — `MedKit` бросит на пустом названии.
     */
    @Test
    fun anInvalidFormWritesNothingAndStaysOpen() = runTest {
        val kits = Kits()
        val form = viewModel(kits)
        form.open(medKitId = null)
        form.edit(MedKitFormPresentationDTO(name = "   "))

        var done = false
        form.save { done = true }

        assertTrue(kits.saved.isEmpty())
        assertTrue(kits.described.isEmpty())
        assertEquals(false, done)
        assertEquals(MedKitFormError.NAME_EMPTY, form.state.value.error)
    }

    /** Правка после отказа снимает подсветку: человек уже исправляет, а не смотрит на ошибку. */
    @Test
    fun typingClearsTheRefusal() = runTest {
        val form = viewModel(Kits())
        form.open(medKitId = null)
        form.edit(MedKitFormPresentationDTO(name = ""))
        form.save {}

        form.edit(MedKitFormPresentationDTO(name = "Дача"))

        assertNull(form.state.value.error)
    }
}
