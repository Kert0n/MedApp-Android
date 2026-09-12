package com.kert0n.medapp.feature.packs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.pack.ExpiryDate
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.projected
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.pack.toPresentationDTO
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Просрочку человек видит в списке, не открывая упаковку и не сверяя дату с календарём в голове
 * (PLAN H3). Выделяется она цветом, значком и словом сразу: цвета одного мало — его не видят ни
 * экранный чтец, ни при дальтонизме.
 *
 * Просроченная упаковка из списка **не исчезает**: пока её не выбросили, она лежит в аптечке, и
 * учёт обязан это показывать (PLAN D3).
 */
@RunWith(AndroidJUnit4::class)
class PackageCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val today: LocalDate = LocalDate.parse("2026-09-12")

    private fun show(expiresOn: ExpiryDate? = null, onOpen: () -> Unit = {}) {
        val dto: PackagePresentationDTO = pack(
            id = PACK,
            name = "Парацетамол",
            quantity = tablets("20"),
            expiresOn = expiresOn
        ).projected().toPresentationDTO()
        compose.setContent {
            MedAppTheme { PackageCard(dto, onOpen = onOpen, today = today) }
        }
    }

    @Test
    fun theCardSaysWhatIsLeftAndUntilWhen() {
        show(expiresOn = expiry("2027-03-31"))

        compose.onNodeWithText("Парацетамол").assertIsDisplayed()
        compose.onNodeWithText("20 таблетка").assertIsDisplayed()
        compose.onNodeWithText("до 03.2027").assertIsDisplayed()
    }

    /**
     * Красная проверка: показать просроченную обычной строкой «до 01.2020» — человек проглядит
     * её в списке, и случай краснеет.
     */
    @Test
    fun anExpiredPackageIsNamedNotOnlyColoured() {
        show(expiresOn = expiry("2020-01-31"))

        compose.onNodeWithText("Просрочено 01.2020").assertIsDisplayed()
        compose.onNodeWithContentDescription("Есть просроченные упаковки").assertIsDisplayed()
    }

    /** Истекающее — предупреждение, а не беда: янтарным, со своим словом. */
    @Test
    fun aPackageAboutToExpireWarnsWithoutCryingWolf() {
        show(expiresOn = expiry("2026-09-14"))

        compose.onNodeWithText("Истекает 14.09.2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Срок годности истекает на днях").assertIsDisplayed()
        compose.onNodeWithContentDescription("Есть просроченные упаковки").assertDoesNotExist()
    }

    /** Срок не указан — так и сказано: пустая строка неотличима от несчитанной. */
    @Test
    fun anUnknownExpiryIsSaidOutLoud() {
        show(expiresOn = null)

        compose.onNodeWithText("срок не указан").assertIsDisplayed()
    }

    @Test
    fun theWholeCardOpensThePackage() {
        var opened = 0
        show(onOpen = { opened++ })

        compose.onNodeWithText("Парацетамол").performClick()

        assertEquals(1, opened)
    }
}
