package com.kert0n.medapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Зелёная палитра продукта (PLAN H3): `primary #1B6B4A`, `primaryContainer #A8F0C6`,
 * `tertiary #3B6470`, `surface #F6FBF3`, `error #BA1A1A`. Остальные роли выведены из этих тонов.
 *
 * Палитра задана явно и целиком, а не одним зерном: Material берёт у схемы готовые роли, и
 * недостающую роль он подставит из умолчаний — то есть сиреневую.
 */
val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F0C6),
    onPrimaryContainer = Color(0xFF00210F),
    inversePrimary = Color(0xFF8CD4AB),

    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),

    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F28),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    surfaceTint = Color(0xFF1B6B4A),
    // Подложки Material: на них стоят панель навигации, карточки и листы. Без них панель
    // навигации берёт умолчание — сиреневое.
    surfaceDim = Color(0xFFD7DBD4),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFEFF1EC),

    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC0C9C0),
    scrim = Color(0xFF000000)
)

/** Та же палитра при другом свете: тона те же, роли переставлены по правилам Material. */
val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8CD4AB),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFFA8F0C6),
    inversePrimary = Color(0xFF1B6B4A),

    secondary = Color(0xFFB5CCBA),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D5),

    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF204C58),
    onTertiaryContainer = Color(0xFFBFE9F8),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    surfaceTint = Color(0xFF8CD4AB),
    surfaceDim = Color(0xFF101410),
    surfaceBright = Color(0xFF363A35),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF191C18),
    surfaceContainer = Color(0xFF1D211C),
    surfaceContainerHigh = Color(0xFF272B26),
    surfaceContainerHighest = Color(0xFF323631),
    inverseSurface = Color(0xFFE1E3DE),
    inverseOnSurface = Color(0xFF2E312E),

    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    scrim = Color(0xFF000000)
)
