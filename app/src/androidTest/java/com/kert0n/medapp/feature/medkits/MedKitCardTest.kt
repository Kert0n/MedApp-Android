package com.kert0n.medapp.feature.medkits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.ui.theme.MedAppTheme
import java.time.Instant
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Аптечка в списке показывает, **что внутри** (PLAN H3 №2): сколько упаковок, сколько просрочено,
 * общая ли она. Список нужен человеку, чтобы решить, куда идти, — одного названия для этого мало.
 *
 * Просрочка и признак общей проверяются не цветом, а словами и подписью значка: цвет не читается
 * ни экранным чтецом, ни при дальтонизме, и по правилу H3 он всегда продублирован.
 */
@RunWith(AndroidJUnit4::class)
class MedKitCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun medKit(
        name: String = "Домашняя",
        location: String? = null,
        packages: Int = 0,
        expired: Int = 0,
        participants: Long = 1
    ) = MedKitPresentationDTO(
        id = Uuid.random(),
        name = name,
        location = location,
        publication = if (participants > 1) MedKit.Publication.PUBLISHED else MedKit.Publication.LOCAL,
        participantCount = participants,
        createdAt = Instant.parse("2026-09-12T12:00:00Z"),
        syncedAt = null,
        isShared = participants > 1,
        acceptsInvitations = participants > 1,
        packages = packages,
        expired = expired
    )

    private fun show(medKit: MedKitPresentationDTO, onOpen: () -> Unit = {}) {
        compose.setContent { MedAppTheme { MedKitCard(medKit, onOpen) } }
    }

    @Test
    fun theCardSaysHowMuchIsInside() {
        show(medKit(packages = 12))

        compose.onNodeWithText("Домашняя").assertIsDisplayed()
        compose.onNodeWithText("12 упаковок").assertIsDisplayed()
    }

    /** Пустая аптечка говорит, что она пуста, а не молчит: молчание неотличимо от незагруженного. */
    @Test
    fun anEmptyMedKitSaysSo() {
        show(medKit(packages = 0))

        compose.onNodeWithText("Пусто").assertIsDisplayed()
    }

    /**
     * Просрочку человек должен увидеть в списке, не открывая аптечку, — и увидеть словами.
     *
     * Красная проверка: убрать строку просрочки, оставив только цвет, — случай краснеет.
     */
    @Test
    fun expiredIsNamedInWordsNotOnlyInColour() {
        show(medKit(packages = 5, expired = 2))

        compose.onNodeWithText("2 просрочены").assertIsDisplayed()
        compose.onNodeWithContentDescription("Есть просроченные упаковки").assertIsDisplayed()
    }

    @Test
    fun aMedKitWithoutExpiredDoesNotWarn() {
        show(medKit(packages = 5, expired = 0))

        compose.onNodeWithText("2 просрочены").assertDoesNotExist()
        compose.onNodeWithContentDescription("Есть просроченные упаковки").assertDoesNotExist()
    }

    /** Общая аптечка отличается значком и числом участников, а не только оттенком. */
    @Test
    fun aSharedMedKitShowsItsParticipants() {
        show(medKit(participants = 3))

        compose.onNodeWithText("Общая · 3 участника").assertIsDisplayed()
        compose.onNodeWithContentDescription("Общая аптечка").assertIsDisplayed()
    }

    @Test
    fun aLocalMedKitIsNotMarkedShared() {
        show(medKit(participants = 1))

        compose.onNodeWithContentDescription("Общая аптечка").assertDoesNotExist()
    }

    @Test
    fun theWholeCardOpensTheMedKit() {
        var opened = 0
        show(medKit(name = "Дача", location = "Полка"), onOpen = { opened++ })

        compose.onNodeWithText("Полка").assertIsDisplayed()
        compose.onNodeWithText("Дача").performClick()

        assertEquals(1, opened)
    }
}
