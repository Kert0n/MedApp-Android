package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.fixture.DirectTransactions
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.FakeVocabulary
import com.kert0n.medapp.fixture.MainDispatcherRule
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.presentation.pack.PackageAmountError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Пересчёт и утилизация (PLAN H3 №9) оставляют след: без него лекарство просто исчезает, и
 * человек не понимает, куда (PLAN D7). Пересчёт называет остаток целиком, утилизация — убыль.
 *
 * Уходящее в ноль спрашивается: архив — решение человека, а не побочный итог арифметики.
 */
class PackageAmountViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")

    private fun TestScope.viewModel(
        packages: FakePackages,
        watch: CoroutineScope = backgroundScope
    ): PackageAmountViewModel {
        val viewModel = PackageAmountViewModel(
            packages = packages,
            adjusting = PackageAdjusting(
                packages = packages,
                medKits = FakeMedKits(medKit()),
                transactions = DirectTransactions,
                clock = Clock.fixed(now, ZoneOffset.UTC)
            ),
            vocabulary = FakeVocabulary()
        )
        // Состояние живёт, пока на него смотрят: у экрана это подписка, у проверки — эта строка.
        watch.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.open(PACK)
        return viewModel
    }

    private fun stored() = FakePackages(pack(id = PACK, quantity = tablets("20")))

    /**
     * Пересчёт записывает увиденное целиком и оставляет в истории оба конца: было 20, стало 17.
     *
     * Красная проверка: писать разницу вместо значения — остаток станет 3, и случай краснеет.
     */
    @Test
    fun recountingWritesWhatWasSeen() = runTest {
        val packages = stored()
        val amount = viewModel(packages)

        amount.edit(amount.state.value.form.copy(amount = "17"))
        var written = false
        amount.submit { written = true }

        assertTrue(written)
        assertEquals(tablets("17"), packages.packages.single().quantity)
        val movement = packages.movements.single() as StockMovement.Recount
        assertEquals(tablets("20"), movement.before)
        assertEquals(tablets("17"), movement.after)
    }

    @Test
    fun disposalWritesWhatWentAway() = runTest {
        val packages = stored()
        val amount = viewModel(packages)
        amount.pick(PackageAmountViewModel.Tab.DISPOSAL)

        amount.edit(amount.state.value.form.copy(amount = "3", note = "рассыпал"))
        amount.submit {}

        assertEquals(tablets("17"), packages.packages.single().quantity)
        val movement = packages.movements.single() as StockMovement.Disposal
        assertEquals(tablets("3"), movement.amount)
        assertEquals(StockMovement.Disposal.Reason.EXPIRED, movement.reason)
        assertEquals("рассыпал", movement.note)
    }

    /** Ноль — решение человека: сначала вопрос, и до ответа не записано ничего. */
    @Test
    fun goingToZeroIsAskedFirst() = runTest {
        val packages = stored()
        val amount = viewModel(packages)

        amount.edit(amount.state.value.form.copy(amount = "0"))
        var written = false
        amount.submit { written = true }

        assertTrue("вопрос должен быть задан", amount.state.value.confirming)
        assertFalse(written)
        assertTrue(packages.movements.isEmpty())
        assertEquals(tablets("20"), packages.packages.single().quantity)
    }

    @Test
    fun aConfirmedZeroArchivesThePackageAndKeepsTheHistory() = runTest {
        val packages = stored()
        val amount = viewModel(packages)
        amount.edit(amount.state.value.form.copy(amount = "0"))
        amount.submit {}

        var written = false
        amount.confirm { written = true }

        assertTrue(written)
        assertEquals(Package.Lifecycle.ARCHIVED, packages.packages.single().lifecycle)
        assertEquals(1, packages.movements.size)
    }

    /** Передумал — значит не записано: молчание подтверждением не считается. */
    @Test
    fun dismissingTheQuestionWritesNothing() = runTest {
        val packages = stored()
        val amount = viewModel(packages)
        amount.edit(amount.state.value.form.copy(amount = "0"))
        amount.submit {}

        amount.dismiss()

        assertFalse(amount.state.value.confirming)
        assertTrue(packages.movements.isEmpty())
    }

    /** Выбросили больше, чем было: в историю идёт ушедшее, а не запрошенное (PLAN D7). */
    @Test
    fun disposingMoreThanThereIsRecordsWhatWasThere() = runTest {
        val packages = stored()
        val amount = viewModel(packages)
        amount.pick(PackageAmountViewModel.Tab.DISPOSAL)
        amount.edit(amount.state.value.form.copy(amount = "50"))

        amount.submit {}
        amount.confirm {}

        val movement = packages.movements.single() as StockMovement.Disposal
        assertEquals(tablets("20"), movement.amount)
        assertEquals(Package.Lifecycle.ARCHIVED, packages.packages.single().lifecycle)
    }

    @Test
    fun disposingNothingIsRefused() = runTest {
        val packages = stored()
        val amount = viewModel(packages)
        amount.pick(PackageAmountViewModel.Tab.DISPOSAL)

        amount.edit(amount.state.value.form.copy(amount = "0"))
        amount.submit {}

        assertEquals(PackageAmountError.NothingToDispose, amount.state.value.error)
        assertTrue(packages.movements.isEmpty())
    }

    @Test
    fun whatIsNotANumberWritesNothing() = runTest {
        val packages = stored()
        val amount = viewModel(packages)

        amount.edit(amount.state.value.form.copy(amount = "семнадцать"))
        amount.submit {}

        assertTrue(amount.state.value.error is PackageAmountError.Amount)
        assertTrue(packages.movements.isEmpty())
        assertEquals(tablets("20"), packages.packages.single().quantity)
    }
}
