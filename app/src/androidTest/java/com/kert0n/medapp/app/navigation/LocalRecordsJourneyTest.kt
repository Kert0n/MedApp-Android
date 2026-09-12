package com.kert0n.medapp.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.HiltTestActivity
import com.kert0n.medapp.ui.theme.MedAppTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Локальный учёт целиком и без сети (PLAN J2.1): человек заводит аптечку, кладёт в неё коробку,
 * пересчитывает остаток и находит лекарство поиском. Здесь настоящий граф и настоящая база —
 * подделок нет, потому что проверяется не экран, а то, что экраны, сценарии и хранение сходятся.
 *
 * Заодно это проверка достижимости (REQ-049): до каждого экрана набора можно дойти руками, и
 * вернуться с него тоже. Недостижимый экран и функция без экрана — одна и та же ошибка.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalRecordsJourneyTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private fun start() {
        compose.setContent { MedAppTheme { MedAppShell() } }
    }

    private fun back() = compose.onNodeWithContentDescription("Назад").performClick()

    /** Заводит аптечку «Домашняя» через экраны 2 и 3 и возвращается к списку. */
    private fun createMedKit() {
        compose.onNodeWithText("Завести аптечку").performClick()
        compose.onNodeWithText("Название").performTextInput("Домашняя")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Домашняя").assertIsDisplayed()
    }

    private fun openMedKit() = compose.onNodeWithText("Домашняя").performClick()

    /**
     * Заводит упаковку через экран 7. Первую зовёт пустой экран, следующие — кнопка внизу:
     * подпись у них одна, и различать их тесту незачем.
     */
    private fun createPackage(name: String = "Парацетамол", amount: String = "20") {
        val invitation = compose.onAllNodesWithText("Завести упаковку")
        if (invitation.fetchSemanticsNodes().isNotEmpty()) {
            invitation.onFirst().performClick()
        } else {
            compose.onNodeWithContentDescription("Завести упаковку").performClick()
        }
        compose.onNodeWithText("Название").performTextInput(name)
        compose.onNodeWithText("Количество").performTextInput(amount)
        compose.onNodeWithText("Единица").performClick()
        compose.onNodeWithText("шт").performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
    }

    /**
     * Сквозной путь без сети: аптечка, упаковка, пересчёт — и всё это видно там, где человек
     * будет это искать.
     *
     * Красная проверка: не записать пересчёт в базу — карточка останется с двадцатью, и случай
     * краснеет.
     */
    @Test
    fun aMedKitAPackageAndARecountLiveWithoutNetwork() {
        start()

        createMedKit()
        openMedKit()
        createPackage()

        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
        compose.onNodeWithText("20 шт").assertIsDisplayed()

        compose.onNodeWithText("Парацетамол").performClick()
        compose.onNodeWithText("Пересчитать или выбросить").performScrollTo().performClick()
        compose.onNodeWithText("Сколько теперь").performTextInput("17")
        compose.onNodeWithText("Записать").performScrollTo().performClick()

        compose.onNodeWithText("17 шт").assertIsDisplayed()
    }

    /** Две одинаково названные коробки остаются двумя: покупка второй не пополняет первую. */
    @Test
    fun twoPackagesOfTheSameNameStayTwo() {
        start()
        createMedKit()
        openMedKit()

        createPackage(amount = "20")
        createPackage(amount = "10")

        compose.onNodeWithText("20 шт").assertIsDisplayed()
        compose.onNodeWithText("10 шт").assertIsDisplayed()
    }

    /** Поиск со списка аптечек ищет по всем сразу — это экран 5 (REQ-029). */
    @Test
    fun searchFromTheMedKitListLooksEverywhere() {
        start()
        createMedKit()
        openMedKit()
        createPackage()
        back()

        compose.onNodeWithText("Найти лекарство во всех аптечках").performClick()
        compose.onNodeWithText("Поиск лекарства").performTextInput("пара")

        compose.onNodeWithText("Все лекарства").assertIsDisplayed()
        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
        compose.onNodeWithText("Домашняя").assertIsDisplayed()
    }

    /**
     * До каждого экрана набора можно дойти руками и вернуться (REQ-049): карточка ведёт к правке,
     * пересчёту и переносу, а правка — обратно к количеству.
     */
    @Test
    fun everyScreenOfLocalRecordsIsReachableAndLeadsBack() {
        start()
        createMedKit()
        openMedKit()
        createPackage()

        compose.onNodeWithText("Парацетамол").performClick()
        compose.onNodeWithText("Сколько есть").assertIsDisplayed()

        compose.onNodeWithText("Перенести в другую аптечку").performScrollTo().performClick()
        compose.onNodeWithText("Перенести упаковку").assertIsDisplayed()
        back()

        compose.onNodeWithText("Править описание").performScrollTo().performClick()
        compose.onNodeWithText("Правка упаковки").assertIsDisplayed()
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithText("Сейчас записано: 20 шт").assertIsDisplayed()
        back()

        compose.onNodeWithText("Правка упаковки").assertIsDisplayed()
    }
}
