package com.mimanga.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Что выбрано в настройках темы. */
enum class ThemeMode { SYSTEM, LIGHT, DARK;

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val DarkColors = darkColorScheme(
    primary = AccentViolet,
    onPrimary = Color(0xFF0B0B12),
    primaryContainer = Color(0xFF241C4D),
    onPrimaryContainer = Color(0xFFD9CEFF),
    secondary = AccentTeal,
    onSecondary = Color(0xFF04201C),
    secondaryContainer = Color(0xFF0E3B36),
    onSecondaryContainer = Color(0xFFB6F2E9),
    tertiary = Color(0xFFFF8FA3),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = Color(0xFF1B1B26),
    outline = DarkOutline,
    outlineVariant = Color(0xFF1B1B26),
    error = ErrorRed,
    scrim = Color(0xCC000000),
)

private val LightColors = lightColorScheme(
    primary = AccentVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF1E1246),
    secondary = AccentTealDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5EF),
    onSecondaryContainer = Color(0xFF052E29),
    tertiary = Color(0xFFD64562),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF0F0F6),
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = Color(0xFFF1F1F7),
    surfaceContainerHigh = Color(0xFFEAEAF3),
    outline = LightOutline,
    outlineVariant = Color(0xFFEDEDF3),
    error = ErrorRed,
    scrim = Color(0x99000000),
)

/** Скругления: крупные у карточек и панелей, средние у полей ввода. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Градиенты и цвета, которых нет в Material-схеме.
 * Держатся отдельно, чтобы экраны не собирали кисти вручную.
 */
data class AppAccents(
    val dark: Boolean,
    val brand: Brush,
    val cardOverlay: Brush,
    val screenGlow: Brush,
    val rating: Color,
    val favorite: Color,
    val statusReading: Color = StatusReading,
    val statusPlanned: Color = StatusPlanned,
    val statusDropped: Color = StatusDropped,
    val statusCompleted: Color = StatusCompleted,
)

val LocalAppAccents = staticCompositionLocalOf {
    AppAccents(
        dark = true,
        brand = Brush.linearGradient(listOf(AccentTeal, AccentViolet)),
        cardOverlay = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
        screenGlow = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        rating = RatingGold,
        favorite = FavoriteRed,
    )
}

@Composable
fun MiMangaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accents = AppAccents(
        dark = dark,
        brand = Brush.linearGradient(
            if (dark) listOf(AccentTeal, AccentIndigo, AccentViolet)
            else listOf(AccentTealDeep, AccentIndigo, AccentVioletDeep)
        ),
        // Подложка под текстом на обложке: снизу почти непрозрачная, сверху
        // прозрачная — название читается на любой картинке.
        cardOverlay = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000))
        ),
        // Свечение вверху экрана.
        //
        // Строго вертикальное и строго до полной прозрачности внизу: шапка
        // рисуется на своей высоте, а всё под ней — чистый фон, поэтому
        // последняя точка градиента ОБЯЗАНА совпадать с фоном. Диагональный
        // вариант этого не давал: у него нижний левый угол шапки оставался
        // почти непрозрачным, и на стыке с фоном была видна линия.
        //
        // Цвет по пути меняется — фиолетовый, чуть розового, бирюзовый, — но
        // прозрачность нарастает рано (уже к середине почти ничего не
        // остаётся), поэтому переход читается как мягкое затемнение, а не как
        // цветная плашка.
        screenGlow = Brush.verticalGradient(
            colorStops = if (dark) arrayOf(
                0.0f to AccentViolet.copy(alpha = 0.18f),
                0.28f to AccentOrchid.copy(alpha = 0.10f),
                0.55f to AccentTeal.copy(alpha = 0.05f),
                0.78f to AccentTeal.copy(alpha = 0.02f),
                1.0f to Color.Transparent,
            ) else arrayOf(
                0.0f to AccentViolet.copy(alpha = 0.12f),
                0.28f to AccentOrchid.copy(alpha = 0.07f),
                0.55f to AccentTealDeep.copy(alpha = 0.04f),
                0.78f to AccentTealDeep.copy(alpha = 0.015f),
                1.0f to Color.Transparent,
            ),
        ),
        rating = RatingGold,
        favorite = FavoriteRed,
    )

    CompositionLocalProvider(LocalAppAccents provides accents) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
