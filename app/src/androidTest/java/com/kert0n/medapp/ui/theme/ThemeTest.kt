package com.kert0n.medapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Палитра продукта своя при любом свете и на любом Android (PLAN H3). Проверяется тем, что тема
 * отдаёт, а не тем, что объявлено: подмена схемы динамическим цветом видна именно здесь.
 *
 * Красная проверка: вернуть теме динамический цвет или шаблонную сиреневую схему — оба случая
 * краснеют.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun schemeOf(darkTheme: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent {
            MedAppTheme(darkTheme = darkTheme) { scheme = MaterialTheme.colorScheme }
        }
        return scheme
    }

    /**
     * Оба набора читаются одной композицией: содержимое правилу задают один раз, а нужны здесь
     * сразу обе схемы — тем и проверяется, что тёмная берёт свой тон, а не тот же самый.
     */
    private fun accents(): Pair<MedAppAccents, MedAppAccents> {
        lateinit var light: MedAppAccents
        lateinit var dark: MedAppAccents
        compose.setContent {
            MedAppTheme(darkTheme = false) { light = MaterialTheme.accents }
            MedAppTheme(darkTheme = true) { dark = MaterialTheme.accents }
        }
        return light to dark
    }

    /**
     * Янтарный приходит от темы, а не от экрана: «занято» и «не хватает» — не беда, и красным о
     * них не говорят (PLAN H3).
     *
     * Красная проверка: не подставить набор в тему — обе схемы вернут светлый, и тёмный случай
     * краснеет.
     */
    @Test
    fun amberComesFromTheThemeAndChangesWithTheLight() {
        val (light, dark) = accents()

        assertEquals(Color(0xFF8A5300), light.reserved)
        assertEquals(Color(0xFFFFB95C), dark.reserved)
    }

    @Test
    fun lightSchemeIsTheGreenPaletteOfThePlan() {
        val scheme = schemeOf(darkTheme = false)

        assertEquals(Color(0xFF1B6B4A), scheme.primary)
        assertEquals(Color(0xFFA8F0C6), scheme.primaryContainer)
        assertEquals(Color(0xFF3B6470), scheme.tertiary)
        assertEquals(Color(0xFFF6FBF3), scheme.surface)
        assertEquals(Color(0xFFBA1A1A), scheme.error)
        // Подложка панели навигации: незаданная роль приходит из умолчаний Material сиреневой,
        // и видно это только на экране — поэтому она названа здесь.
        assertEquals(Color(0xFFEAEFE7), scheme.surfaceContainer)
    }

    /** Тёмная схема — те же тона при другом свете, а не умолчания Material. */
    @Test
    fun darkSchemeKeepsTheSameHues() {
        val scheme = schemeOf(darkTheme = true)

        assertEquals(Color(0xFF8CD4AB), scheme.primary)
        assertEquals(Color(0xFFA8F0C6), scheme.onPrimaryContainer)
        assertEquals(Color(0xFF101410), scheme.surface)
        assertEquals(Color(0xFF1B6B4A), scheme.inversePrimary)
        assertEquals(Color(0xFF1D211C), scheme.surfaceContainer)
    }
}
