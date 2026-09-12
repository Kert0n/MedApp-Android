package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.DirectTransactions
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Экран 11 (PLAN H3): куда переложить коробку. Общие аптечки не предлагаются — перенос в общую
 * требует связи (C3), — и экран говорит об этом строкой, а не прячет их молча.
 */
@RunWith(AndroidJUnit4::class)
class PackageTransferScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val packages = FakePackages(pack(id = PACK, quantity = tablets("20")))

    private fun show(vararg others: MedKit) {
        val medKits = FakeMedKits(medKit(id = HOME_KIT), *others)
        val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)
        val viewModel = PackageTransferViewModel(
            packages = packages,
            adjusting = PackageAdjusting(packages, medKits, DirectTransactions, clock),
            medKits = medKits,
            clock = clock
        )
        compose.setContent {
            MedAppTheme { PackageTransferScreen(PACK, onDone = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun aLocalMedKitIsOfferedAndTheTransferIsWritten() {
        show(medKit(id = SHARED_KIT, name = "Дача"))

        compose.onNodeWithText("Дача").performClick()
        compose.onNodeWithText("Перенести").performClick()

        compose.waitForIdle()
        assertEquals(SHARED_KIT, packages.packages.single().medKit.id)
    }

    /**
     * Красная проверка: молча отфильтровать общие — человек не найдёт свою аптечку в списке и не
     * поймёт почему, а случай краснеет.
     */
    @Test
    fun sharedMedKitsAreExplainedNotHidden() {
        show(
            medKit(
                id = SHARED_KIT,
                name = "Общая",
                publication = MedKit.Publication.PUBLISHED,
                participantCount = 3
            )
        )

        compose.onNodeWithText("Общая").assertDoesNotExist()
        compose.onNodeWithText(
            "Переносить некуда: остальные ваши аптечки общие, а перенос в общую требует связи с сервером."
        ).assertIsDisplayed()
    }

    @Test
    fun withNowhereToMoveTheScreenSaysSo() {
        show()

        compose.onNodeWithText(
            "Переносить некуда: другой аптечки пока нет. Заведите её — и упаковку можно будет переложить."
        ).assertIsDisplayed()
    }
}
