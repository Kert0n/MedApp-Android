package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Содержимое аптечки (PLAN H3 №4) и все лекарства (№5). Два пустых состояния здесь разные:
 * пустая аптечка зовёт завести упаковку, неудачный поиск — сбросить запрос. Спутать их значит
 * предложить человеку не то.
 */
@RunWith(AndroidJUnit4::class)
class MedKitContentsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val medKits = FakeMedKits(
        medKit(id = HOME_KIT, name = "Домашняя"),
        medKit(id = SHARED_KIT, name = "Дача")
    )

    private fun show(medKitId: Uuid? = HOME_KIT, vararg packs: Package) {
        val viewModel = MedKitContentsViewModel(
            packages = FakePackages(*packs),
            medKits = medKits,
            clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)
        )
        compose.setContent {
            MedAppTheme {
                MedKitContentsScreen(
                    medKitId = medKitId,
                    onBack = {},
                    onOpen = {},
                    onAdd = {},
                    viewModel = viewModel
                )
            }
        }
    }

    @Test
    fun theListShowsWhatIsInside() {
        show(HOME_KIT, pack(id = PACK, name = "Парацетамол", quantity = tablets("20")))

        compose.onNodeWithText("Домашняя").assertIsDisplayed()
        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
        compose.onNodeWithText("20 таблетка").assertIsDisplayed()
    }

    @Test
    fun anEmptyMedKitInvitesToAddThings() {
        show(HOME_KIT)

        compose.onNodeWithText(
            "В этой аптечке пока ничего нет. Заведите первую упаковку — достаточно названия, количества и единицы."
        ).assertIsDisplayed()
    }

    /**
     * Красная проверка: показать одно и то же сообщение в обоих случаях — человеку предложат
     * завести упаковку там, где он просто неудачно искал, и случай краснеет.
     */
    @Test
    fun aFruitlessSearchOffersToResetNotToAdd() {
        show(HOME_KIT, pack(id = PACK, name = "Парацетамол"))

        compose.onNodeWithText("Поиск лекарства").performTextInput("аспирин")

        compose.onNodeWithText("Ничего не нашлось. Попробуйте другое слово или сбросьте фильтр.")
            .assertIsDisplayed()
        compose.onNodeWithText("Сбросить").assertIsDisplayed()
    }

    @Test
    fun resettingBringsTheListBack() {
        show(HOME_KIT, pack(id = PACK, name = "Парацетамол"))
        compose.onNodeWithText("Поиск лекарства").performTextInput("аспирин")

        compose.onNodeWithText("Сбросить").performClick()

        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
    }

    /** Сузить список есть чем, и чем именно — видно сразу (PLAN H3 №4). */
    @Test
    fun thereIsSomethingToNarrowTheListWith() {
        show(HOME_KIT, pack(id = PACK, name = "Парацетамол"))

        compose.onNodeWithText("Просроченные").assertIsDisplayed()
        compose.onNodeWithText("Истекают").assertIsDisplayed()
        compose.onNodeWithText("На курсе").assertIsDisplayed()
        // Полоса чипов прокручивается: порядок стоит за ними, и до него нужно доехать.
        compose.onNodeWithText("Порядок: по названию").performScrollTo().assertIsDisplayed()
    }

    /** На экране всех лекарств у каждой строки видно, из какой она аптечки. */
    @Test
    fun everywhereEachRowNamesItsMedKit() {
        show(
            null,
            pack(id = PACK, name = "Парацетамол"),
            pack(id = OTHER_PACK, name = "Нурофен", medKit = medKit(id = SHARED_KIT).ref)
        )

        compose.onNodeWithText("Все лекарства").assertIsDisplayed()
        compose.onNodeWithText("Домашняя").assertIsDisplayed()
        compose.onNodeWithText("Дача").assertIsDisplayed()
    }
}
