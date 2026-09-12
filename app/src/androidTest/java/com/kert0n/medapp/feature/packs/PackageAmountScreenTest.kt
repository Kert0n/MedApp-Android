package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.FakeVocabulary
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Экран 9 (PLAN H3): две вкладки — «пересчитал» и «выбросил». Ноль спрашивается словами, а не
 * случается сам: упаковка уходит в архив, и человек должен это выбрать.
 */
@RunWith(AndroidJUnit4::class)
class PackageAmountScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val packages = FakePackages(pack(id = PACK, quantity = tablets("20")))

    private fun show() {
        val viewModel = PackageAmountViewModel(
            packages = packages,
            adjusting = PackageAdjusting(
                packages,
                Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)
            ),
            vocabulary = FakeVocabulary()
        )
        compose.setContent {
            MedAppTheme { PackageAmountScreen(PACK, onDone = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun bothWaysToChangeTheAmountAreOnTheScreen() {
        show()

        compose.onNodeWithText("Пересчитал").assertIsDisplayed()
        compose.onNodeWithText("Выбросил").assertIsDisplayed()
        compose.onNodeWithText("Сейчас записано: 20 таблетка").assertIsDisplayed()
    }

    /** Пересчёт называет остаток целиком — так и написано под полем, а не подразумевается. */
    @Test
    fun recountingSaysItReplacesTheAmount() {
        show()

        compose.onNodeWithText("Сколько теперь").assertIsDisplayed()
        compose.onNodeWithText("Запишется новое количество целиком, а не разница.").assertIsDisplayed()
    }

    @Test
    fun disposalAsksWhy() {
        show()

        compose.onNodeWithText("Выбросил").performClick()

        compose.onNodeWithText("Сколько выбросили").assertIsDisplayed()
        compose.onNodeWithText("Почему").assertIsDisplayed()
    }

    /**
     * Ноль спрашивается, и до ответа не записано ничего.
     *
     * Красная проверка: писать сразу — вопрос не появится, и случай краснеет.
     */
    @Test
    fun goingToZeroIsAskedInWords() {
        show()

        compose.onNodeWithText("Сколько теперь").performTextInput("0")
        compose.onNodeWithText("Записать").performScrollTo().performClick()

        compose.onNodeWithText("Упаковка уйдёт в архив").assertIsDisplayed()
        compose.onNodeWithText(
            "В ней не останется ничего. История сохранится: видно будет, сколько было и куда делось."
        ).assertIsDisplayed()
        assertTrue(packages.movements.isEmpty())
    }

    @Test
    fun aConfirmedZeroIsWritten() {
        show()
        compose.onNodeWithText("Сколько теперь").performTextInput("0")
        compose.onNodeWithText("Записать").performScrollTo().performClick()

        compose.onNodeWithText("Записать и в архив").performClick()

        compose.waitForIdle()
        assertEquals(Package.Lifecycle.ARCHIVED, packages.packages.single().lifecycle)
        assertEquals(1, packages.movements.size)
    }
}
