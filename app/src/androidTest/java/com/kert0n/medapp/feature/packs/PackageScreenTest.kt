package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.fixture.FakeMedKits
import com.kert0n.medapp.fixture.FakePackages
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Карточка упаковки (PLAN H3 №6): человек открывает её, чтобы узнать, сколько есть и до какого
 * срока. Незаполненного она не показывает, а занятое и просроченное называет словами, не одним
 * цветом.
 */
@RunWith(AndroidJUnit4::class)
class PackageScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)

    private var edited: Uuid? = null

    private fun show(pkg: Package) {
        val viewModel = PackageViewModel(
            packages = FakePackages(pkg),
            medKits = FakeMedKits(medKit(id = HOME_KIT, location = "Верхний ящик")),
            clock = clock
        )
        compose.setContent {
            MedAppTheme {
                PackageScreen(pkg.id, onBack = {}, onEdit = { edited = it }, viewModel = viewModel)
            }
        }
    }

    @Test
    fun theCardLeadsWithHowMuchIsThere() {
        show(pack(name = "Парацетамол", quantity = tablets("20")))

        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
        compose.onNodeWithText("Сколько есть").assertIsDisplayed()
        compose.onNodeWithText("20 таблетка").assertIsDisplayed()
    }

    /** Пустых строк нет: «производитель: —» занимает место и не сообщает ничего. */
    @Test
    fun onlyWhatIsKnownIsShown() {
        show(pack(manufacturer = null, category = null, description = null, form = null))

        compose.onNodeWithText("Что это").assertDoesNotExist()
        compose.onNodeWithText("Производитель").assertDoesNotExist()
    }

    @Test
    fun whatIsFilledInIsShown() {
        show(pack(manufacturer = "Фармстандарт", category = "Жаропонижающие"))

        compose.onNodeWithText("Что это").assertIsDisplayed()
        compose.onNodeWithText("Фармстандарт").assertIsDisplayed()
        compose.onNodeWithText("Жаропонижающие").assertIsDisplayed()
    }

    /**
     * Чужая бронь — не беда и не просрочка: она янтарная, и рядом с цветом стоят слова и значок.
     *
     * Красная проверка: оставить одну заливку без строки — случай краснеет.
     */
    @Test
    fun whatOthersReservedIsNamedInWords() {
        show(pack(id = PACK, quantity = tablets("20"), claims = Claims(total = BigDecimal("5"))))

        compose.onNodeWithText("Занято другими: 5 таблетка").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Часть упаковки заявлена другими участниками")
            .assertIsDisplayed()
        compose.onNodeWithText("Доступно мне").performScrollTo().assertIsDisplayed()
    }

    /** Три одинаковых числа подряд читаются как ошибка: одинаковое не повторяется (PLAN D4). */
    @Test
    fun whatIsNotDifferentIsNotRepeated() {
        show(pack(quantity = tablets("20")))

        compose.onNodeWithText("Доступно мне").assertDoesNotExist()
        compose.onNodeWithText("Свободно любому").assertDoesNotExist()
    }

    @Test
    fun anExpiredPackageSaysSoInWords() {
        show(pack(expiresOn = expiry("2020-01-31")))

        compose.onNodeWithText("Просрочено 01.2020").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLivePackageShowsHowLongItLasts() {
        show(pack(expiresOn = expiry("2027-03-31")))

        compose.onNodeWithText("до 03.2027").performScrollTo().assertIsDisplayed()
    }

    /** Где лежит — по названию аптечки: тождество человеку ничего не говорит. */
    @Test
    fun theMedKitIsNamed() {
        show(pack())

        compose.onNodeWithText("Где лежит").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Домашняя").performScrollTo().assertIsDisplayed()
    }

    /** Правка описания начинается отсюда и знает, из какой аптечки пачка (PLAN H3 №8). */
    @Test
    fun theCardLeadsToEditing() {
        show(pack())

        compose.onNodeWithText("Править описание").performScrollTo().performClick()

        assertEquals(HOME_KIT, edited)
    }
}
