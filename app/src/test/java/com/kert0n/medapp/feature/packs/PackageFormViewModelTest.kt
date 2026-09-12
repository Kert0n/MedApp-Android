package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakeMovements
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.FakeVocabulary
import com.kert0n.medapp.fixture.DirectTransactions
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.MainDispatcherRule
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.presentation.pack.PackageFormError
import com.kert0n.medapp.presentation.pack.PackageFormPresentationDTO
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Форма упаковки: пока человек печатает, пачки ещё нет, а записанная пачка приходит в базу
 * вместе со своим приходом (PLAN D7, F5). Отменённая и неверная формы не оставляют следов.
 */
class PackageFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")
    private val packages = FakePackages()
    private val medKits = FakeMedKits(medKit(id = HOME_KIT))
    private val movements = FakeMovements()

    private fun viewModel(packages: FakePackages = this.packages) = PackageFormViewModel(
        creation = PackageCreation(
            packages = packages,
            medKits = medKits,
            movements = movements,
            transactions = DirectTransactions,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        ),
        packages = packages,
        vocabulary = FakeVocabulary(),
        medKits = medKits,
        clock = Clock.fixed(now, ZoneOffset.UTC)
    )

    private fun filled(name: String = "Парацетамол", amount: String = "20") =
        PackageFormPresentationDTO(
            medKitId = HOME_KIT,
            name = name,
            amount = amount,
            unit = UnitPresentationDTO(TABLETS.id, TABLETS.name)
        )

    /**
     * Пачка и её приход ложатся вместе: остаток, взявшийся ниоткуда, ломает историю с первого
     * же дня (PLAN D7).
     *
     * Красная проверка: убрать запись прихода — движение не найдётся, и случай краснеет.
     */
    @Test
    fun aPackageArrivesTogetherWithItsReceipt() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled())

        var created: Uuid? = null
        form.save { created = it }

        val written = packages.packages.single()
        assertEquals(created, written.id)
        assertEquals("Парацетамол", written.name)
        assertEquals(tablets("20"), written.quantity)
        val receipt = movements.recorded.single() as StockMovement.Receipt
        assertEquals(written.id, receipt.pkg.id)
        assertEquals(tablets("20"), receipt.amount)
        assertEquals(now, receipt.occurredAt)
    }

    /** Две одинаково названные пачки — две пачки: покупка другой коробки не пополняет старую. */
    @Test
    fun twoPackagesOfTheSameNameStayTwo() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled())
        form.save {}

        val second = viewModel()
        second.open(HOME_KIT)
        second.edit(filled(amount = "10"))
        second.save {}

        assertEquals(2, packages.packages.size)
        assertEquals(listOf(tablets("20"), tablets("10")), packages.packages.map { it.quantity })
    }

    @Test
    fun anInvalidFormWritesNothingAndStaysOpen() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled(name = "   "))

        var done = false
        form.save { done = true }

        assertTrue(packages.packages.isEmpty())
        assertTrue(movements.recorded.isEmpty())
        assertEquals(false, done)
        assertEquals(PackageFormError.NameEmpty, form.state.value.error)
    }

    /** Заполненная, но не сохранённая форма не заводит ничего: ввод живёт в экране, а не в базе. */
    @Test
    fun anAbandonedFormWritesNothing() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled())

        assertTrue(packages.packages.isEmpty())
        assertTrue(movements.recorded.isEmpty())
    }

    /** Аптечку удалили, пока форма была открыта: это отказ на месте выбора, а не пачка нигде. */
    @Test
    fun aMedKitThatIsGoneRefusesTheWrite() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled())
        medKits.forget(HOME_KIT)

        var done = false
        form.save { done = true }

        assertEquals(false, done)
        assertTrue(packages.packages.isEmpty())
        assertEquals(PackageFormError.MedKitMissing, form.state.value.error)
    }

    /** Форма подставляет аптечку, из которой человек пришёл: выбирать её заново незачем. */
    @Test
    fun theMedKitIsTakenFromWhereThePersonCameFrom() = runTest {
        val form = viewModel()

        form.open(HOME_KIT)

        assertEquals(HOME_KIT, form.state.value.form.medKitId)
    }

    /** Выбирать есть из чего: аптечки, единицы и формы приходят потоками, а не аргументами. */
    @Test
    fun theFormOffersWhatCanBeChosen() = runTest {
        val form = viewModel()

        form.open(HOME_KIT)

        assertEquals(listOf("Домашняя"), form.state.value.medKits.map { it.name })
        assertEquals(listOf("таблетка", "мл"), form.state.value.units.map { it.name })
        assertEquals(listOf("таблетки", "капсулы"), form.state.value.forms.map { it.name })
    }

    /** Правка открывается на том, что записано, а не на пустых полях (PLAN H3 №8). */
    @Test
    fun anOpenedFormShowsWhatIsStored() = runTest {
        val stored = FakePackages(pack(id = PACK, name = "Парацетамол", quantity = tablets("20")))
        val form = viewModel(stored)

        form.open(HOME_KIT, PACK)

        assertEquals("Парацетамол", form.state.value.form.name)
        assertEquals("20", form.state.value.form.amount)
        assertTrue(form.state.value.isEditing)
    }

    /**
     * Правка меняет описание и только его: количество двигают пересчёт и утилизация, и только
     * они оставляют след в истории (PLAN D7, F5).
     *
     * Красная проверка: сохранять правку общей записью пачки — остаток станет тем, что экран
     * прочитал когда-то раньше, и случай краснеет.
     */
    @Test
    fun editingDoesNotTouchTheAmount() = runTest {
        val stored = FakePackages(pack(id = PACK, name = "Парацетамол", quantity = tablets("20")))
        val form = viewModel(stored)
        form.open(HOME_KIT, PACK)

        form.edit(form.state.value.form.copy(name = "Панадол", amount = "5"))
        form.save {}

        val written = stored.packages.single()
        assertEquals("Панадол", written.name)
        assertEquals(tablets("20"), written.quantity)
        assertTrue("правка не пишет движений", movements.recorded.isEmpty())
    }

    /** Пачки больше нет — писать некуда, и форма не притворяется, что сохранила. */
    @Test
    fun aPackageThatIsGoneStopsTheForm() = runTest {
        val form = viewModel(FakePackages())
        form.open(HOME_KIT, PACK)

        form.edit(form.state.value.form.copy(name = "Панадол", unit = UnitPresentationDTO(TABLETS.id, TABLETS.name)))
        var done = false
        form.save { done = true }

        assertEquals(false, done)
        assertTrue(form.state.value.gone)
    }

    @Test
    fun typingClearsTheRefusal() = runTest {
        val form = viewModel()
        form.open(HOME_KIT)
        form.edit(filled(name = ""))
        form.save {}

        form.edit(filled())

        assertNull(form.state.value.error)
    }
}
