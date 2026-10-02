package com.kampusagi.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kampusagi.android.domain.model.ThemeMode

// Blue on white, like a clean social app; the dark scheme keeps the same blue family.
private val Blue = Color(0xFF1D6FE8)
private val BlueLight = Color(0xFF5B9CFF)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EEFF),
    onPrimaryContainer = Color(0xFF0B2E6B),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F4FD),
    onSecondaryContainer = Color(0xFF073B52),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF2F5FA),
    onSurfaceVariant = Color(0xFF5B6778),
    surfaceTint = Blue,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F5FA),
    surfaceContainerHighest = Color(0xFFE9EEF5),
    outline = Color(0xFFD5DCE6),
    outlineVariant = Color(0xFFE8EDF3),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF00214F),
    primaryContainer = Color(0xFF0E3A7A),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF002C3F),
    secondaryContainer = Color(0xFF0B3A52),
    onSecondaryContainer = Color(0xFFD4F0FD),
    tertiary = Color(0xFFFB7185),
    onTertiary = Color(0xFF4C0519),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0B0F17),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFF9AA6B8),
    surfaceTint = BlueLight,
    surfaceContainerLowest = Color(0xFF070A10),
    surfaceContainerLow = Color(0xFF0F141E),
    surfaceContainer = Color(0xFF111723),
    surfaceContainerHigh = Color(0xFF18202D),
    surfaceContainerHighest = Color(0xFF212B3A),
    outline = Color(0xFF2E3A4D),
    outlineVariant = Color(0xFF1E2735),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
fun KampusAgiTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
