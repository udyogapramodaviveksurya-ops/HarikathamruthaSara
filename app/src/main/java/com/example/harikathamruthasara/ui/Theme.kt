package com.example.harikathamruthasara.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.harikathamruthasara.data.ColorStyle
import com.example.harikathamruthasara.data.DarkMode

// ---------------------------------------------------------------- "Festival" scheme
// Saffron (primary) · lotus pink (secondary) · peacock teal (tertiary) on warm cream. Every on-colour pair meets
// WCAG AA (4.5:1) for body text; containers are soft enough for long reading.

private val FestivalLight = lightColorScheme(
    primary = Color(0xFFC43E00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9), onPrimaryContainer = Color(0xFF3A0F00),
    secondary = Color(0xFFAD1B63), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E6), onSecondaryContainer = Color(0xFF3E0020),
    tertiary = Color(0xFF00696E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA6EEF2), onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F1), onBackground = Color(0xFF231A14),
    surface = Color(0xFFFFF8F1), onSurface = Color(0xFF231A14),
    surfaceVariant = Color(0xFFF5DED3), onSurfaceVariant = Color(0xFF53433B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E7), surfaceContainer = Color(0xFFFCEADE),
    surfaceContainerHigh = Color(0xFFF7E4D7), surfaceContainerHighest = Color(0xFFF1DED1),
    outline = Color(0xFF86736A), outlineVariant = Color(0xFFD8C2B7),
    inverseSurface = Color(0xFF392E28), inverseOnSurface = Color(0xFFFFEDE4), inversePrimary = Color(0xFFFFB693),
)

private val FestivalDark = darkColorScheme(
    primary = Color(0xFFFFB693), onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF8A2D00), onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFFFB0CD), onSecondary = Color(0xFF630035),
    secondaryContainer = Color(0xFF86064A), onSecondaryContainer = Color(0xFFFFD8E6),
    tertiary = Color(0xFF8AD2D6), onTertiary = Color(0xFF00373A),
    tertiaryContainer = Color(0xFF004F53), onTertiaryContainer = Color(0xFFA6EEF2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A110D), onBackground = Color(0xFFF1DFD6),
    surface = Color(0xFF1A110D), onSurface = Color(0xFFF1DFD6),
    surfaceVariant = Color(0xFF53433B), onSurfaceVariant = Color(0xFFD8C2B7),
    surfaceContainerLowest = Color(0xFF140C08),
    surfaceContainerLow = Color(0xFF231914), surfaceContainer = Color(0xFF271D18),
    surfaceContainerHigh = Color(0xFF322722), surfaceContainerHighest = Color(0xFF3E322C),
    outline = Color(0xFFA08D83), outlineVariant = Color(0xFF53433B),
    inverseSurface = Color(0xFFF1DFD6), inverseOnSurface = Color(0xFF392E28), inversePrimary = Color(0xFFAA3600),
)

// ---------------------------------------------------------------- playful accent tones
/** One cheerful colour family: a soft [container], text/icons on it ([on]), and a bold [accent] for rings, dots and stripes. */
@Immutable
data class Tone(val container: Color, val on: Color, val accent: Color)

/** Six festival tones: marigold, lotus, peacock, leaf, sky, berry. Used to give every tile, sandhi and badge its own colour. */
@Immutable
class Tones(val all: List<Tone>) {
    val marigold get() = all[0]
    val lotus get() = all[1]
    val peacock get() = all[2]
    val leaf get() = all[3]
    val sky get() = all[4]
    val berry get() = all[5]
    /** a stable colour for item number [i] (cycles through the rainbow) */
    operator fun get(i: Int): Tone = all[((i % all.size) + all.size) % all.size]
}

private val LightTones = Tones(listOf(
    Tone(Color(0xFFFFE08A), Color(0xFF3D2E00), Color(0xFFE5A000)),   // marigold
    Tone(Color(0xFFFFD6E7), Color(0xFF4A0028), Color(0xFFE0457B)),   // lotus
    Tone(Color(0xFFB8F1EC), Color(0xFF00332F), Color(0xFF0E9A97)),   // peacock
    Tone(Color(0xFFD3F2BE), Color(0xFF0E3300), Color(0xFF3E9C48)),   // leaf
    Tone(Color(0xFFD7E3FF), Color(0xFF001B3F), Color(0xFF3D6FE0)),   // sky
    Tone(Color(0xFFEBDDFF), Color(0xFF25005A), Color(0xFF8752D6)),   // berry
))

private val DarkTones = Tones(listOf(
    Tone(Color(0xFF5A4300), Color(0xFFFFE08A), Color(0xFFFFC93C)),
    Tone(Color(0xFF6E1440), Color(0xFFFFD6E7), Color(0xFFFF8AB3)),
    Tone(Color(0xFF00504B), Color(0xFFB8F1EC), Color(0xFF4FD8D2)),
    Tone(Color(0xFF1E5011), Color(0xFFD3F2BE), Color(0xFF8AD86E)),
    Tone(Color(0xFF1E3F80), Color(0xFFD7E3FF), Color(0xFF8FB3FF)),
    Tone(Color(0xFF4F2B8A), Color(0xFFEBDDFF), Color(0xFFC6A6FF)),
))

val LocalTones = staticCompositionLocalOf { LightTones }
val LocalIsDark = staticCompositionLocalOf { false }

/** The playful tones of the current theme (light or dark). */
val tones: Tones @Composable get() = LocalTones.current

object HkColors {
    val good = Color(0xFF2E7D32)
    val goodBg = Color(0x332E7D32)
    val bad = Color(0xFFC62828)
    val badBg = Color(0x33C62828)
    /** a sound to fix (orange: said, but not quite right) */
    val sound = Color(0xFFB45309)
    val soundBg = Color(0x33E8890C)
    val gold = Color(0xFFE0A100)
    // brand (from the logo)
    val navy = Color(0xFF162856)
    val navyDeep = Color(0xFF0E1A3A)
    val brandGold = Color(0xFFE8B33A)
    /** the playful header: logo navy → temple violet → lotus magenta */
    val header = listOf(Color(0xFF162856), Color(0xFF3A2A8F), Color(0xFF9A2A72))
}

// ---------------------------------------------------------------- shape & type
/** Rounder than stock Material 3: friendly, pebble-like cards. */
private val HkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** The stock M3 scale (system font: it carries every Indic script), with bolder headings for a cheerful voice. */
private val HkType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Whether the app should be dark, from the setting (or the phone). */
@Composable
fun isAppDark(mode: DarkMode): Boolean = when (mode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

/** Material You (wallpaper colours) needs Android 12. */
val canUseWallpaperColors: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun HkTheme(style: ColorStyle = ColorStyle.FESTIVAL, darkMode: DarkMode = DarkMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isAppDark(darkMode)
    val ctx = LocalContext.current
    val scheme = when {
        style == ColorStyle.WALLPAPER && canUseWallpaperColors -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FestivalDark
        else -> FestivalLight
    }
    CompositionLocalProvider(LocalTones provides (if (dark) DarkTones else LightTones), LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, shapes = HkShapes, typography = HkType, content = content)
    }
}
