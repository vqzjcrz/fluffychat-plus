package dev.hush.client.ui.theme

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import dev.hush.client.data.AppSettings

@Immutable
data class HushPalette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    /** Chat background. */
    val background: Color,
    /** Channel list / sheets. */
    val surface: Color,
    /** Server rail, the darkest (or deepest) layer. */
    val rail: Color,
    /** Inputs, cards, code blocks. */
    val elevated: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    val danger: Color = Color(0xFFED4245),
    val success: Color = Color(0xFF3BA55C),
    val warning: Color = Color(0xFFF0B232),
) {
    val link: Color get() = accent

    fun withAccent(color: Color): HushPalette =
        copy(accent = color, onAccent = if (color.luminance() > 0.5f) Color(0xFF111214) else Color.White)
}

object Themes {
    const val DYNAMIC = "dynamic"

    val Midnight = HushPalette(
        id = "midnight", name = "Midnight", isDark = true,
        background = Color(0xFF101216), surface = Color(0xFF16191E), rail = Color(0xFF0B0C0F),
        elevated = Color(0xFF1F232A), text = Color(0xFFE7E9EC), muted = Color(0xFF8B929D),
        accent = Color(0xFF8B9BFF), onAccent = Color(0xFF0B0C0F), divider = Color(0xFF252A32),
    )
    val Amoled = HushPalette(
        id = "amoled", name = "AMOLED", isDark = true,
        background = Color(0xFF000000), surface = Color(0xFF070707), rail = Color(0xFF000000),
        elevated = Color(0xFF141414), text = Color(0xFFEDEDED), muted = Color(0xFF8A8A8A),
        accent = Color(0xFF5AA9FF), onAccent = Color(0xFF000000), divider = Color(0xFF1C1C1C),
    )
    val Classic = HushPalette(
        id = "classic", name = "Classic", isDark = true,
        background = Color(0xFF313338), surface = Color(0xFF2B2D31), rail = Color(0xFF1E1F22),
        elevated = Color(0xFF383A40), text = Color(0xFFDBDEE1), muted = Color(0xFF949BA4),
        accent = Color(0xFF5865F2), onAccent = Color.White, divider = Color(0xFF3F4147),
    )
    val Nord = HushPalette(
        id = "nord", name = "Nord", isDark = true,
        background = Color(0xFF2E3440), surface = Color(0xFF2A303B), rail = Color(0xFF242933),
        elevated = Color(0xFF3B4252), text = Color(0xFFECEFF4), muted = Color(0xFF97A1B3),
        accent = Color(0xFF88C0D0), onAccent = Color(0xFF2E3440), divider = Color(0xFF3B4252),
    )
    val Dracula = HushPalette(
        id = "dracula", name = "Dracula", isDark = true,
        background = Color(0xFF282A36), surface = Color(0xFF21222C), rail = Color(0xFF191A21),
        elevated = Color(0xFF343746), text = Color(0xFFF8F8F2), muted = Color(0xFF9EA4C4),
        accent = Color(0xFFBD93F9), onAccent = Color(0xFF191A21), divider = Color(0xFF343746),
    )
    val Mocha = HushPalette(
        id = "mocha", name = "Mocha", isDark = true,
        background = Color(0xFF1E1E2E), surface = Color(0xFF181825), rail = Color(0xFF11111B),
        elevated = Color(0xFF313244), text = Color(0xFFCDD6F4), muted = Color(0xFFA6ADC8),
        accent = Color(0xFFCBA6F7), onAccent = Color(0xFF11111B), divider = Color(0xFF313244),
    )
    val Gruvbox = HushPalette(
        id = "gruvbox", name = "Gruvbox", isDark = true,
        background = Color(0xFF282828), surface = Color(0xFF1D2021), rail = Color(0xFF141617),
        elevated = Color(0xFF3C3836), text = Color(0xFFEBDBB2), muted = Color(0xFFA89984),
        accent = Color(0xFFFABD2F), onAccent = Color(0xFF282828), divider = Color(0xFF3C3836),
    )
    val RosePine = HushPalette(
        id = "rosepine", name = "Rosé Pine", isDark = true,
        background = Color(0xFF191724), surface = Color(0xFF1F1D2E), rail = Color(0xFF13111E),
        elevated = Color(0xFF26233A), text = Color(0xFFE0DEF4), muted = Color(0xFF908CAA),
        accent = Color(0xFFEBBCBA), onAccent = Color(0xFF191724), divider = Color(0xFF2A2740),
    )
    val TokyoNight = HushPalette(
        id = "tokyo", name = "Tokyo Night", isDark = true,
        background = Color(0xFF1A1B26), surface = Color(0xFF16161E), rail = Color(0xFF101014),
        elevated = Color(0xFF24283B), text = Color(0xFFC0CAF5), muted = Color(0xFF7A80A6),
        accent = Color(0xFF7AA2F7), onAccent = Color(0xFF101014), divider = Color(0xFF24283B),
    )
    val Paper = HushPalette(
        id = "paper", name = "Paper", isDark = false,
        background = Color(0xFFFAFAF7), surface = Color(0xFFF1F0EB), rail = Color(0xFFE6E4DD),
        elevated = Color(0xFFFFFFFF), text = Color(0xFF1E2126), muted = Color(0xFF6A6F78),
        accent = Color(0xFF3B5BDB), onAccent = Color.White, divider = Color(0xFFE1DFD8),
    )
    val Solarized = HushPalette(
        id = "solarized", name = "Solarized", isDark = false,
        background = Color(0xFFFDF6E3), surface = Color(0xFFF5EEDA), rail = Color(0xFFEEE8D5),
        elevated = Color(0xFFFFFBF0), text = Color(0xFF073642), muted = Color(0xFF657B83),
        accent = Color(0xFF268BD2), onAccent = Color.White, divider = Color(0xFFE4DCC6),
    )

    val builtIn: List<HushPalette> =
        listOf(Midnight, Amoled, Classic, Nord, Dracula, Mocha, Gruvbox, RosePine, TokyoNight, Paper, Solarized)

    val dynamicAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun resolve(id: String, systemDark: Boolean, context: Context): HushPalette {
        if (id == DYNAMIC) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Midnight
            val scheme = if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return fromScheme(scheme, systemDark)
        }
        return builtIn.firstOrNull { it.id == id } ?: Midnight
    }

    /** Every option shown in the theme picker, with Material You first when supported. */
    fun options(context: Context, systemDark: Boolean): List<HushPalette> =
        if (dynamicAvailable) listOf(resolve(DYNAMIC, systemDark, context)) + builtIn else builtIn

    private fun fromScheme(s: ColorScheme, dark: Boolean) = HushPalette(
        id = DYNAMIC, name = "Material You", isDark = dark,
        background = s.surface, surface = s.surfaceContainerLow, rail = s.surfaceContainerLowest,
        elevated = s.surfaceContainerHigh, text = s.onSurface, muted = s.onSurfaceVariant,
        accent = s.primary, onAccent = s.onPrimary, divider = s.outlineVariant, danger = s.error,
    )
}

fun HushPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.25f).compositeOver(surface),
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = elevated,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = elevated,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = danger,
        onError = Color.White,
        outline = muted,
        outlineVariant = divider,
        surfaceBright = elevated,
        surfaceDim = rail,
        surfaceContainerLowest = rail,
        surfaceContainerLow = surface,
        surfaceContainer = elevated,
        surfaceContainerHigh = elevated,
        surfaceContainerHighest = elevated,
    )
}

val LocalPalette = staticCompositionLocalOf { Themes.Midnight }
val LocalSettings = staticCompositionLocalOf { AppSettings() }

@Composable
fun HushTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val palette = remember(settings.themeId, settings.accent, systemDark) {
        val base = Themes.resolve(settings.themeId, systemDark, context)
        settings.accent?.let { base.withAccent(Color(it)) } ?: base
    }

    val activity = context as? ComponentActivity
    DisposableEffect(palette.isDark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (palette.isDark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    MaterialTheme(colorScheme = palette.toColorScheme()) {
        CompositionLocalProvider(LocalPalette provides palette, LocalSettings provides settings) {
            content()
        }
    }
}
