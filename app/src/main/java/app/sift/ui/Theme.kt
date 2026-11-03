package app.sift.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.sift.engine.Severity
import app.sift.engine.Verdict

@Composable
fun SiftTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun Verdict.color(): Color = when (this) {
    Verdict.LOOKS_CLEAN -> Color(0xFF2E9E5B)
    Verdict.CAUTION -> Color(0xFFD9A200)
    Verdict.SUSPICIOUS -> Color(0xFFE8710A)
    Verdict.DANGEROUS -> Color(0xFFD93025)
}

fun Severity.color(): Color = when (this) {
    Severity.INFO -> Color(0xFF6B8AA8)
    Severity.LOW -> Color(0xFFC9A400)
    Severity.MEDIUM -> Color(0xFFE8710A)
    Severity.HIGH -> Color(0xFFD93025)
    Severity.CRITICAL -> Color(0xFFB0006E)
}
