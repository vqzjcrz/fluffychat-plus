package app.switchboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.switchboard.R
import app.switchboard.core.Prefs

/** The colors every screen draws with. */
@Immutable
data class SbColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val bubbleIn: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val ownBubble: Color,
    val onOwnBubble: Color,
    val ownLink: Color,
    val danger: Color,
    val onDanger: Color,
    val outlineBubbles: Boolean = false,
    val isLight: Boolean = false,
)

data class ThemePreset(val id: String, val name: String, val colors: SbColors)

object Themes {
    val presets = listOf(
        ThemePreset("mono", "Mono", SbColors(
            background = Color(0xFF000000), surface = Color(0xFF0E0E10), raised = Color(0xFF18181B),
            bubbleIn = Color(0xFF18181B), line = Color(0xFF1F1F23), text = Color(0xFFFFFFFF),
            textMuted = Color(0xFFA1A1AA), textFaint = Color(0xFF80808A), accent = Color(0xFFD4FF3F),
            onAccent = Color(0xFF0A0A0A), ownBubble = Color(0xFFD4FF3F), onOwnBubble = Color(0xFF0A0A0A),
            ownLink = Color(0xFF2E4A00), danger = Color(0xFFFF5A5F), onDanger = Color(0xFF0A0A0A))),
        ThemePreset("midnight", "Midnight", SbColors(
            background = Color(0xFF16171A), surface = Color(0xFF1E1F23), raised = Color(0xFF25262B),
            bubbleIn = Color(0xFF25262B), line = Color(0xFF2C2D32), text = Color(0xFFEDEEF0),
            textMuted = Color(0xFFA6A9B0), textFaint = Color(0xFF8A8E96), accent = Color(0xFF8F9CFF),
            onAccent = Color(0xFF0B0B0D), ownBubble = Color(0xFF4553D6), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFDCE0FF), danger = Color(0xFFFF6B70), onDanger = Color(0xFF0B0B0D))),
        ThemePreset("amoled", "AMOLED", SbColors(
            background = Color(0xFF000000), surface = Color(0xFF0E0E10), raised = Color(0xFF1A1A1D),
            bubbleIn = Color(0xFF1A1A1D), line = Color(0xFF1F1F23), text = Color(0xFFFFFFFF),
            textMuted = Color(0xFFA1A1AA), textFaint = Color(0xFF80808A), accent = Color(0xFF8AB4FF),
            onAccent = Color(0xFF0A0A0A), ownBubble = Color(0xFF2B4FD8), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFD7E3FF), danger = Color(0xFFFF5A5F), onDanger = Color(0xFF0A0A0A))),
        ThemePreset("graphite", "Graphite", SbColors(
            background = Color(0xFF1C1C1E), surface = Color(0xFF242426), raised = Color(0xFF2C2C2F),
            bubbleIn = Color(0xFF2C2C2F), line = Color(0xFF343437), text = Color(0xFFF2F2F3),
            textMuted = Color(0xFFA9A9AF), textFaint = Color(0xFF8E8E94), accent = Color(0xFF64D2B3),
            onAccent = Color(0xFF0A0A0A), ownBubble = Color(0xFF13695A), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFBFF5E6), danger = Color(0xFFFF7A7A), onDanger = Color(0xFF0A0A0A))),
        ThemePreset("nord", "Nord", SbColors(
            background = Color(0xFF242933), surface = Color(0xFF2B313C), raised = Color(0xFF313844),
            bubbleIn = Color(0xFF313844), line = Color(0xFF3A4150), text = Color(0xFFECEFF4),
            textMuted = Color(0xFFB4BCCB), textFaint = Color(0xFF98A0AF), accent = Color(0xFF88C0D0),
            onAccent = Color(0xFF1A1E25), ownBubble = Color(0xFF3D5A80), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFCFE6F0), danger = Color(0xFFF08A92), onDanger = Color(0xFF1A1E25))),
        ThemePreset("mocha", "Mocha", SbColors(
            background = Color(0xFF1E1E2E), surface = Color(0xFF252536), raised = Color(0xFF2C2D3F),
            bubbleIn = Color(0xFF2C2D3F), line = Color(0xFF35364B), text = Color(0xFFCDD6F4),
            textMuted = Color(0xFFA6ADC8), textFaint = Color(0xFF8E94B0), accent = Color(0xFFCBA6F7),
            onAccent = Color(0xFF11111B), ownBubble = Color(0xFF5B4A9E), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFE8DBFF), danger = Color(0xFFF38BA8), onDanger = Color(0xFF11111B))),
        ThemePreset("forest", "Forest", SbColors(
            background = Color(0xFF1A1F1C), surface = Color(0xFF202622), raised = Color(0xFF262D29),
            bubbleIn = Color(0xFF262D29), line = Color(0xFF2F3732), text = Color(0xFFE7EEE9),
            textMuted = Color(0xFFA3B1A8), textFaint = Color(0xFF8A988F), accent = Color(0xFF8BD3A0),
            onAccent = Color(0xFF0D130F), ownBubble = Color(0xFF2F6B45), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFCDEFD8), danger = Color(0xFFF28B82), onDanger = Color(0xFF0D130F))),
        ThemePreset("paper", "Paper", SbColors(
            background = Color(0xFFF5F6F8), surface = Color(0xFFFFFFFF), raised = Color(0xFFE9EBF0),
            bubbleIn = Color(0xFFFFFFFF), line = Color(0xFFE1E4EA), text = Color(0xFF15161A),
            textMuted = Color(0xFF5B6070), textFaint = Color(0xFF696E7E), accent = Color(0xFF2F54EB),
            onAccent = Color(0xFFFFFFFF), ownBubble = Color(0xFF2F54EB), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFE3E9FF), danger = Color(0xFFC62828), onDanger = Color(0xFFFFFFFF),
            outlineBubbles = true, isLight = true)),
        ThemePreset("sand", "Sand", SbColors(
            background = Color(0xFFF3EEE6), surface = Color(0xFFFFFCF7), raised = Color(0xFFE8E1D5),
            bubbleIn = Color(0xFFFFFCF7), line = Color(0xFFE2DACD), text = Color(0xFF2A231E),
            textMuted = Color(0xFF6B5F55), textFaint = Color(0xFF76695E), accent = Color(0xFFA04A1C),
            onAccent = Color(0xFFFFFFFF), ownBubble = Color(0xFF8C3F17), onOwnBubble = Color(0xFFFFFFFF),
            ownLink = Color(0xFFFFE2CF), danger = Color(0xFFB42318), onDanger = Color(0xFFFFFFFF),
            outlineBubbles = true, isLight = true)),
    )

    const val MATERIAL_YOU = "material_you"

    fun byId(id: String) = presets.firstOrNull { it.id == id } ?: presets[0]

    /** Builds colors from the wallpaper (Android 12+) or a seed color. */
    fun materialYou(scheme: ColorScheme, light: Boolean) = SbColors(
        background = scheme.surface, surface = scheme.surfaceContainer, raised = scheme.surfaceContainerHigh,
        bubbleIn = scheme.surfaceContainerHigh, line = scheme.outlineVariant, text = scheme.onSurface,
        textMuted = scheme.onSurfaceVariant, textFaint = scheme.outline, accent = scheme.primary,
        onAccent = scheme.onPrimary, ownBubble = scheme.primaryContainer, onOwnBubble = scheme.onPrimaryContainer,
        ownLink = scheme.primary, danger = scheme.error, onDanger = scheme.onError, isLight = light,
    )
}

/** Picks black or white text for a background. */
fun readableOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF0A0A0A) else Color.White

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Swaps only the accent colors for a space's own color; backgrounds and text stay. */
fun SbColors.withAccent(color: Color?): SbColors {
    if (color == null) return this
    var accent: Color = color
    // Keep the accent visible against the background.
    var i = 0
    while (contrast(accent, background) < 3f && i < 12) {
        accent = if (isLight) lerp(accent, Color.Black, 0.12f) else lerp(accent, Color.White, 0.12f)
        i++
    }
    val onAccent = readableOn(accent)
    return copy(
        accent = accent, onAccent = onAccent,
        ownBubble = accent, onOwnBubble = onAccent,
        ownLink = if (onAccent == Color.White) Color(0xFFE8ECFF) else lerp(accent, Color.Black, 0.7f),
    )
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_display_black, FontWeight.Black),
)

val LocalSb = staticCompositionLocalOf { Themes.presets[0].colors }

object Sbt {
    val c: SbColors @Composable get() = LocalSb.current
    val title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.6).sp)
    val kicker = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp)
    val rowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val tiny = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
}

private fun scheme(c: SbColors): ColorScheme {
    val base = if (c.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = lerp(c.background, c.accent, if (c.isLight) 0.16f else 0.24f), onPrimaryContainer = c.text,
        secondary = c.accent, onSecondary = c.onAccent,
        secondaryContainer = c.raised, onSecondaryContainer = c.text,
        tertiary = c.accent, onTertiary = c.onAccent,
        background = c.background, onBackground = c.text,
        surface = c.background, onSurface = c.text,
        surfaceVariant = c.raised, onSurfaceVariant = c.textMuted,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.raised,
        surfaceContainerHighest = lerp(c.raised, c.text, 0.06f),
        surfaceTint = Color.Transparent,
        outline = c.textFaint, outlineVariant = c.line,
        error = c.danger, onError = c.onDanger,
        inverseSurface = c.text, inverseOnSurface = c.background,
    )
}

@Composable
fun currentColors(): SbColors {
    val id by Prefs.theme.collectAsState()
    val seed by Prefs.seedColor.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(id, seed, dark) {
        if (id == Themes.MATERIAL_YOU) {
            if (Build.VERSION.SDK_INT >= 31) {
                val s = if (dark) dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
                Themes.materialYou(s, !dark)
            } else {
                Themes.byId("midnight").colors.withAccent(Color(seed))
            }
        } else Themes.byId(id).colors
    }
}

@Composable
fun SwitchboardTheme(colors: SbColors = currentColors(), content: @Composable () -> Unit) {
    val typography = remember {
        val t = Typography()
        Typography(
            displayLarge = t.displayLarge.copy(fontFamily = InterDisplay),
            displayMedium = t.displayMedium.copy(fontFamily = InterDisplay),
            displaySmall = t.displaySmall.copy(fontFamily = InterDisplay),
            headlineLarge = t.headlineLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Black),
            headlineMedium = t.headlineMedium.copy(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold),
            headlineSmall = t.headlineSmall.copy(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold),
            titleLarge = t.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
            titleSmall = t.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
            bodyLarge = t.bodyLarge.copy(fontFamily = Inter),
            bodyMedium = t.bodyMedium.copy(fontFamily = Inter),
            bodySmall = t.bodySmall.copy(fontFamily = Inter),
            labelLarge = t.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
            labelMedium = t.labelMedium.copy(fontFamily = Inter),
            labelSmall = t.labelSmall.copy(fontFamily = Inter),
        )
    }
    CompositionLocalProvider(LocalSb provides colors) {
        MaterialTheme(colorScheme = scheme(colors), typography = typography, content = content)
    }
}
