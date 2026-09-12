package com.kert0n.medapp.feature.packs

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.DirectTransactions
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.MainDispatcherRule
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Перенос упаковки (PLAN H3 №11): человек переложил коробку, и учёт узнаёт об этом. Общие
 * аптечки не предлагаются — перенос в общую требует связи (PLAN C3, E6), — но и не исчезают
 * молча: экран о них говорит.
 */
class PackageTransferViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-09-12T12:00:00Z")

    private val packages = FakePackages(pack(id = PACK, quantity = tablets("20")))

    private fun TestScope.viewModel(medKits: FakeMedKits): PackageTransferViewModel {
        val viewModel = PackageTransferViewModel(
            packages = packages,
            adjusting = PackageAdjusting(
                packages = packages,
                medKits = medKits,
                transactions = DirectTransactions,
                clock = Clock.fixed(now, ZoneOffset.UTC)
            ),
            medKits = medKits,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.open(PACK)
        return viewModel
    }

    private fun kits(vararg extra: MedKit) = FakeMedKits(medKit(id = HOME_KIT), *extra)

    /** Переносить в ту же аптечку нечего: пачка там уже лежит. */
    @Test
    fun theMedKitItAlreadyLiesInIsNotOffered() = runTest {
        val transfer = viewModel(kits(medKit(id = SHARED_KIT, name = "Дача")))

        assertEquals(listOf("Дача"), transfer.state.value.candidates.map { it.name })
    }

    /**
     * Перенос переставляет место и только его: остаток не трогается, а истории расхода он не
     * касается вовсе — истрачено ничего не было (PLAN D7).
     *
     * Красная проверка: списать при переносе хоть сколько-нибудь — остаток изменится, и случай
     * краснеет.
     */
    @Test
    fun aTransferMovesThePackageAndSpendsNothing() = runTest {
        val transfer = viewModel(kits(medKit(id = SHARED_KIT, name = "Дача")))
        transfer.choose(SHARED_KIT)

        var moved = false
        transfer.transfer { moved = true }

        assertTrue(moved)
        val written = packages.packages.single()
        assertEquals(SHARED_KIT, written.medKit.id)
        assertEquals(tablets("20"), written.quantity)
        assertTrue("перенос ничего не тратит", packages.movements.isEmpty())
    }

    /** Общая аптечка не предлагается, но и не пропадает без слова: об этом говорит экран. */
    @Test
    fun sharedMedKitsAreNotOfferedAndThatIsSaidOutLoud() = runTest {
        val shared = medKit(
            id = SHARED_KIT,
            name = "Общая",
            publication = MedKit.Publication.PUBLISHED,
            participantCount = 3
        )
        val transfer = viewModel(kits(shared))

        assertTrue(transfer.state.value.candidates.isEmpty())
        assertTrue(transfer.state.value.hasSharedOnes)
    }

    /** Ничего не выбрано — ничего не записано: перенос «куда-нибудь» не бывает. */
    @Test
    fun nothingIsWrittenUntilSomewhereIsChosen() = runTest {
        val transfer = viewModel(kits(medKit(id = SHARED_KIT, name = "Дача")))

        var moved = false
        transfer.transfer { moved = true }

        assertFalse(moved)
        assertTrue(packages.movements.isEmpty())
    }

    /** Аптечку удалили, пока человек выбирал: переносить некуда, и записано ничего не будет. */
    @Test
    fun aTargetThatIsGoneWritesNothing() = runTest {
        val medKits = kits(medKit(id = SHARED_KIT, name = "Дача"))
        val transfer = viewModel(medKits)
        transfer.choose(SHARED_KIT)
        medKits.forget(SHARED_KIT)

        var moved = false
        transfer.transfer { moved = true }

        assertFalse(moved)
        assertTrue(packages.movements.isEmpty())
        assertEquals(HOME_KIT, packages.packages.single().medKit.id)
    }
}
