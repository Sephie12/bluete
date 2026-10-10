package de.sephie.bluete.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.sephie.bluete.data.ThemeMode
import de.sephie.bluete.domain.Phase

// Farben aus dem Bloom-Logo (pinke Blume)
private val Rose = Color(0xFFD4788C)
private val RoseHell = Color(0xFFF2D1D9)
private val RoseDunkel = Color(0xFFA85566)
private val Lavendel = Color(0xFF9B7EBD)
private val Tuerkis = Color(0xFF7DB9B6)
private val Orange = Color(0xFFE8A87C)

private val HellesSchema = lightColorScheme(
    primary = RoseDunkel,
    onPrimary = Color.White,
    primaryContainer = RoseHell,
    onPrimaryContainer = Color(0xFF4A1826),
    secondary = Color(0xFF7A5F98),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3F6),
    onSecondaryContainer = Color(0xFF2F1B45),
    tertiary = Color(0xFF3F7E7B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5ECEA),
    onTertiaryContainer = Color(0xFF0E3533),
    background = Color(0xFFFFF9F9),
    onBackground = Color(0xFF2D2D2D),
    surface = Color(0xFFFFF9F9),
    onSurface = Color(0xFF2D2D2D),
    surfaceVariant = Color(0xFFF7E8EB),
    onSurfaceVariant = Color(0xFF6B5A5E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF3F5),
    surfaceContainer = Color(0xFFFCEEF1),
    surfaceContainerHigh = Color(0xFFF8E7EB),
    surfaceContainerHighest = Color(0xFFF3E0E5),
    outline = Color(0xFFB59AA1),
    outlineVariant = Color(0xFFEDE5E5)
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFFF0A7B8),
    onPrimary = Color(0xFF4A1826),
    primaryContainer = Color(0xFF7A3A4B),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFD4C5E2),
    onSecondary = Color(0xFF392A4B),
    secondaryContainer = Color(0xFF514063),
    onSecondaryContainer = Color(0xFFF0E3FA),
    tertiary = Color(0xFFA6D6D3),
    onTertiary = Color(0xFF113634),
    tertiaryContainer = Color(0xFF2B4F4D),
    onTertiaryContainer = Color(0xFFD5ECEA),
    background = Color(0xFF1C1416),
    onBackground = Color(0xFFF2E4E7),
    surface = Color(0xFF1C1416),
    onSurface = Color(0xFFF2E4E7),
    surfaceVariant = Color(0xFF3A2C30),
    onSurfaceVariant = Color(0xFFD8C2C7),
    surfaceContainerLowest = Color(0xFF160F11),
    surfaceContainerLow = Color(0xFF241A1D),
    surfaceContainer = Color(0xFF291E21),
    surfaceContainerHigh = Color(0xFF33272A),
    surfaceContainerHighest = Color(0xFF3E3135),
    outline = Color(0xFF9F8B90),
    outlineVariant = Color(0xFF4A3B3F)
)

/** Zusätzliche Farben für Kalender und Phasen. */
@Immutable
data class CycleColors(
    val period: Color,
    val periodSoft: Color,
    val fertile: Color,
    val ovulation: Color,
    val ovulationSoft: Color,
    val follicular: Color,
    val luteal: Color,
    val onStrong: Color
) {
    fun phase(p: Phase): Color = when (p) {
        Phase.MENSTRUATION -> period
        Phase.FOLLIKEL -> follicular
        Phase.EISPRUNG -> ovulation
        Phase.LUTEAL -> luteal
    }
}

private val HelleZyklusFarben = CycleColors(
    period = Rose,
    periodSoft = RoseHell,
    fertile = Color(0xFFC5E0DF),
    ovulation = Orange,
    ovulationSoft = Color(0xFFFFE0C2),
    follicular = Tuerkis,
    luteal = Lavendel,
    onStrong = Color.White
)

private val DunkleZyklusFarben = CycleColors(
    period = Color(0xFFE38FA2),
    periodSoft = Color(0xFF5C3440),
    fertile = Color(0xFF2E4D4B),
    ovulation = Color(0xFFF0B98F),
    ovulationSoft = Color(0xFF5A4232),
    follicular = Color(0xFF8FCAC7),
    luteal = Color(0xFFB9A2D6),
    onStrong = Color(0xFF231417)
)

val LocalCycleColors = staticCompositionLocalOf { HelleZyklusFarben }

@Composable
fun BlueteTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.HELL -> false
        ThemeMode.DUNKEL -> true
    }
    CompositionLocalProvider(LocalCycleColors provides if (dark) DunkleZyklusFarben else HelleZyklusFarben) {
        MaterialTheme(
            colorScheme = if (dark) DunklesSchema else HellesSchema,
            content = content
        )
    }
}
