package org.zorel.gb3android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class Gb3Palette(
    val conversationColors: List<Color>,
    val name: Color,
    val url: Color,
    val clock: Color,
    val muted: Color,
    val bigorno: Color,
    val highlight: Color,
    val highlightText: Color,
    val spoilerBackground: Color,
    val spoilerRevealed: Color,
    val filteredBackground: Color,
    val filteredText: Color,
    val totoz: Color,
    val mine: Color,
    val reply: Color,
    val selectedBackground: Color,
)

val LocalGb3Palette = staticCompositionLocalOf<Gb3Palette> {
    error("Gb3Palette not provided")
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A7FF),
    onPrimary = Color(0xFF1B1035),
    primaryContainer = Color(0xFF4B32B8),
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = Color(0xFF4DE0C2),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF005143),
    onSecondaryContainer = Color(0xFFB8FFEF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF202124),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF2B2B2E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF3A3A3F),
    onSurfaceVariant = Color(0xFFC9C4CF),
    outline = Color(0xFF8E8A97),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF4E0000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A3EC8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A4B00),
    onTertiary = Color.White,
    background = Color(0xFFEEEEEE),
    onBackground = Color(0xFF1E1E1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFFF1EFF5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkPalette = Gb3Palette(
    conversationColors = listOf(
        Color(0xFFFF8A80),
        Color(0xFF82B1FF),
        Color(0xFFB9F6CA),
        Color(0xFFFFE082),
        Color(0xFFCE93D8),
        Color(0xFFFFB74D),
        Color(0xFF80DEEA),
        Color(0xFFBCAAA4),
    ),
    name = Color(0xFFFFA94D),
    url = Color(0xFF5FAFFF),
    clock = Color(0xFF4DE0C2),
    muted = Color(0xFF9A9A9A),
    bigorno = Color(0xFFFF6B6B),
    highlight = Color(0xFF5A3EC8),
    highlightText = Color.White,
    spoilerBackground = Color(0xFF141416),
    spoilerRevealed = Color(0xFFFF6B6B),
    filteredBackground = Color(0xFF3A3A3D),
    filteredText = Color(0xFF9A9A9A),
    totoz = Color(0xFF6EE7A8),
    mine = Color(0xFFFFD75F),
    reply = Color(0xFFFF8A8A),
    selectedBackground = Color(0xFF3C335F),
)

private val LightPalette = Gb3Palette(
    conversationColors = listOf(
        Color(0xFFB71C1C),
        Color(0xFF0D47A1),
        Color(0xFF1B5E20),
        Color(0xFF7A5B00),
        Color(0xFF6A1B9A),
        Color(0xFFBF360C),
        Color(0xFF006064),
        Color(0xFF4E342E),
    ),
    name = Color(0xFFB35C00),
    url = Color(0xFF1565C0),
    clock = Color(0xFF00796B),
    muted = Color(0xFF666666),
    bigorno = Color(0xFFC62828),
    highlight = Color(0xFFFFE082),
    highlightText = Color(0xFF3A2600),
    spoilerBackground = Color(0xFF202020),
    spoilerRevealed = Color(0xFFC62828),
    filteredBackground = Color(0xFFDCDCDC),
    filteredText = Color(0xFF777777),
    totoz = Color(0xFF1B7A3E),
    mine = Color(0xFFB26A00),
    reply = Color(0xFFC62828),
    selectedBackground = Color(0xFFE7DEFF),
)

@Composable
fun Gb3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalGb3Palette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}
