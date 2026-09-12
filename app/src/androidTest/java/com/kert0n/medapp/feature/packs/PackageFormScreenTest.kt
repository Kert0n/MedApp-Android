package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.fixture.DirectTransactions
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakeMovements
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.FakeVocabulary
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Форма упаковки (PLAN H3 №7): сверху четыре поля, без которых упаковки не бывает, — остальное
 * человек раскрывает сам. Все поля есть, но пугать двенадцатью сразу нечем: он принёс коробку из
 * аптеки, а не заполняет карточку товара.
 */
@RunWith(AndroidJUnit4::class)
class PackageFormScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val medKits = FakeMedKits(medKit(id = HOME_KIT))

    private lateinit var viewModel: PackageFormViewModel

    private fun show() {
        val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)
        viewModel = PackageFormViewModel(
            creation = PackageCreation(
                packages = FakePackages(),
                medKits = medKits,
                movements = FakeMovements(),
                transactions = DirectTransactions,
                clock = clock
            ),
            vocabulary = FakeVocabulary(),
            medKits = medKits,
            clock = clock
        )
        compose.setContent {
            MedAppTheme { PackageFormScreen(HOME_KIT, onDone = {}, viewModel = viewModel) }
        }
    }

    @Test
    fun theFourRequiredFieldsAreVisibleAtOnce() {
        show()

        compose.onNodeWithText("Аптечка").assertIsDisplayed()
        compose.onNodeWithText("Название").assertIsDisplayed()
        compose.onNodeWithText("Количество").assertIsDisplayed()
        compose.onNodeWithText("Единица").assertIsDisplayed()
    }

    /** Необязательные поля есть все до одного (ТЗ 4.1.1.1), но открывает их человек. */
    @Test
    fun theRestIsThereButNotInTheWay() {
        show()

        compose.onNodeWithText("Производитель").assertDoesNotExist()
        compose.onNodeWithText("Необязательное").performClick()

        compose.onNodeWithText("Производитель").assertIsDisplayed()
        compose.onNodeWithText("Срок годности").assertIsDisplayed()
        compose.onNodeWithText("Цена").performScrollTo().assertIsDisplayed()
    }

    /**
     * Кнопка сохранения не гаснет: отказ называет, чего не хватает, — и называет **поле**.
     *
     * Красная проверка: гасить кнопку по пустой форме — человек видит мёртвую кнопку и не
     * узнаёт причины, а случай краснеет.
     */
    @Test
    fun anEmptyFormIsRefusedInWords() {
        show()

        compose.onNodeWithText("Сохранить").performScrollTo().performClick()

        compose.onNodeWithText("Выберите единицу: без неё количество ничего не измеряет.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Названа та беда, что ближе к делу: единица выбрана, количество есть, а названия нет. */
    @Test
    fun aNamelessPackageIsRefusedByItsName() {
        show()
        compose.runOnIdle {
            viewModel.edit(
                viewModel.state.value.form.copy(
                    amount = "20",
                    unit = UnitPresentationDTO(TABLETS.id, TABLETS.name)
                )
            )
        }

        compose.onNodeWithText("Сохранить").performScrollTo().performClick()

        compose.onNodeWithText("Название нужно: без него упаковку не найти.")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
