package app.switchboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Mxc
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

private val avatarPalette = listOf(
    Color(0xFFE5484D), Color(0xFFF76B15), Color(0xFFFFC53D), Color(0xFF46A758), Color(0xFF12A594),
    Color(0xFF0090FF), Color(0xFF3E63DD), Color(0xFF8E4EC6), Color(0xFFD6409F), Color(0xFF978365),
    Color(0xFF5B8A72), Color(0xFF6E7FA8),
)

val grayFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

fun colorFor(key: String): Color = avatarPalette[(key.hashCode() and 0x7fffffff) % avatarPalette.size]

fun initialOf(name: String): String {
    val n = name.trimStart('#', '@', '!', ' ')
    val cp = n.codePointAtOrNull(0) ?: return "?"
    return String(Character.toChars(cp)).uppercase()
}

private fun String.codePointAtOrNull(i: Int) = if (length > i) codePointAt(i) else null

/** Round picture for a person, chat or space, with initials while loading or when there's none. */
@Composable
fun Avatar(url: String?, name: String, size: Dp = 48.dp, key: String = name, shape: androidx.compose.ui.graphics.Shape = CircleShape, modifier: Modifier = Modifier, grayscale: Boolean = false) {
    val context = LocalContext.current
    val px = (size.value * 2).toInt().coerceIn(48, 512)
    val mxc = remember(url, px) { Mxc.avatar(url, px) }
    val fallback: @Composable () -> Unit = {
        Box(Modifier.size(size).clip(shape).background(if (grayscale) Color(0xFF3A3A40) else colorFor(key)), contentAlignment = Alignment.Center) {
            Text(initialOf(name), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
    if (mxc == null) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = remember(mxc) { ImageRequest.Builder(context).data(mxc).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = if (grayscale) grayFilter else null,
        alpha = if (grayscale) 0.75f else 1f,
        modifier = modifier.size(size).clip(shape),
        loading = { fallback() },
        error = { fallback() },
    )
}

/** A round icon button with a thin outline, used in headers. */
@Composable
fun CircleButton(icon: ImageVector, description: String?, size: Dp = 42.dp, onClick: () -> Unit) {
    val c = Sbt.c
    Box(
        Modifier.size(size).clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = c.text, modifier = Modifier.size(size * 0.48f))
    }
}

/** Small uppercase label above titles and sections. */
@Composable
fun Kicker(text: String, color: Color = Sbt.c.textMuted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Sbt.kicker, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Sbt.c.line)

/** Text tabs with an accent underline under the selected one. */
@Composable
fun TabRowPlain(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Sbt.c
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        tabs.forEachIndexed { i, t ->
            Column(Modifier.clickable { onSelect(i) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.Start) {
                Text(
                    t, fontFamily = Inter, fontSize = 15.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (i == selected) c.text else c.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == selected) c.accent else Color.Transparent))
            }
        }
    }
}

/** Count badge. Red with @ for mentions, accent otherwise, gray when muted. */
@Composable
fun CountBadge(count: Long, mention: Boolean, muted: Boolean = false) {
    val c = Sbt.c
    val bg = when { mention -> c.danger; muted -> c.raised; else -> c.accent }
    val fg = when { mention -> c.onDanger; muted -> c.textMuted; else -> c.onAccent }
    val label = (if (mention) "@" else "") + (if (count > 999) "999+" else if (count > 0) count.toString() else "")
    if (label.isEmpty()) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(bg))
        return
    }
    Box(
        Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(bg).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Header for pushed screens: back arrow, title and optional actions. */
@Composable
fun ScreenHeader(title: String, kicker: String? = null, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val c = Sbt.c
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (kicker != null) Kicker(kicker)
            Text(title, fontFamily = app.switchboard.ui.theme.InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** One tappable row in settings-style lists. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Sbt.c
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (danger) c.danger else c.textMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (danger) c.danger else c.text)
            if (subtitle != null) Text(subtitle, style = Sbt.small, color = c.textMuted)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SectionLabel(text: String) {
    Kicker(text, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp))
}
