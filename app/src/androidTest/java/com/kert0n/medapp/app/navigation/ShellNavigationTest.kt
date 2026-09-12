package com.kert0n.medapp.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.HiltTestActivity
import com.kert0n.medapp.ui.theme.MedAppTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Оболочка: пять мест внизу, и место, где стоит человек, переживает пересоздание (PLAN H3, J3).
 * Уровень «Экран» по PLAN J1.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // Оболочка держит экраны, а те берут свои ViewModel у графа: окну нужно быть его точкой входа.
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    /**
     * Вкладку от заголовка экрана отличает не текст, а то, что её выбирают: «Аптечки» стоит и
     * внизу, и в шапке списка, и совпадение слов — не повод считать их одним узлом.
     */
    private fun tab(name: String) = compose.onNode(hasText(name) and isSelectable())

    @Test
    fun allFivePlacesAreThereAndTheFirstIsSelected() {
        compose.setContent { MedAppTheme { MedAppShell() } }

        for (name in listOf("Аптечки", "План", "Сканер", "Аналитика", "Настройки")) {
            tab(name).assertIsDisplayed()
        }
        tab("Аптечки").assertIsSelected()
    }

    @Test
    fun tappingAPlaceGoesThere() {
        compose.setContent { MedAppTheme { MedAppShell() } }

        tab("Аналитика").performClick()

        tab("Аналитика").assertIsSelected()
        tab("Аптечки").assertIsNotSelected()
    }

    /**
     * Поворот и смерть процесса — не действие человека: место остаётся тем же. Состояние держит
     * навигация, и восстанавливается оно из `SavedStateHandle`; [StateRestorationTester] проходит
     * ровно этот путь — сохранение и восстановление, а не построение заново.
     *
     * Красная проверка: собрать `NavHostController` мимо `rememberNavController` (без
     * `rememberSaveable`) — вкладка возвращается к «Аптечкам».
     */
    @Test
    fun thePlaceSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MedAppTheme { MedAppShell() } }
        tab("Настройки").performClick()
        tab("Настройки").assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        tab("Настройки").assertIsSelected()
        tab("Аптечки").assertIsNotSelected()
    }

    /**
     * Крупный шрифт разметку не ломает: подписи и содержимое остаются на экране при двойном
     * масштабе (PLAN H3, J3).
     */
    @Test
    fun largeFontKeepsEverythingOnScreen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                MedAppTheme { MedAppShell(Modifier.fillMaxSize()) }
            }
        }

        tab("Аптечки").assertIsDisplayed()
        tab("Настройки").assertIsDisplayed()
        // Содержимое места, а не только его подписи: у пустого списка аптечек это его рассказ.
        compose.onNodeWithText("Аптечек пока нет", substring = true).assertIsDisplayed()
    }
}
