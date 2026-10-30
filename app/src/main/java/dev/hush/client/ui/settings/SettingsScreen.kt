package dev.hush.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hush.client.data.AppSettings
import dev.hush.client.data.AvatarShape
import dev.hush.client.data.MessageDensity
import dev.hush.client.discord.Message
import dev.hush.client.discord.User
import dev.hush.client.discord.newNonce
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.chat.MdResolver
import dev.hush.client.ui.chat.MessageRow
import dev.hush.client.ui.components.UserAvatar
import dev.hush.client.ui.theme.HushPalette
import dev.hush.client.ui.theme.LocalPalette
import dev.hush.client.ui.theme.LocalSettings
import dev.hush.client.ui.theme.Themes
import kotlin.math.roundToInt

private val ACCENTS = listOf(
    0xFF5865F2, 0xFF4DA3FF, 0xFF2DD4BF, 0xFF43B581, 0xFFF0B232,
    0xFFF97316, 0xFFED4245, 0xFFEC4899, 0xFFA855F7,
)

private object PreviewResolver : MdResolver {
    override fun user(id: String): String? = "you"
    override fun channel(id: String): String? = "general"
    override fun role(id: String): String? = "role"
    override val use24h: Boolean = false
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val me by vm.me.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    val update: ((AppSettings) -> AppSettings) -> Unit = { vm.updateSettings(it) }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
                }
                Text("Settings", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = p.divider, thickness = 0.5.dp)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            PreviewCard()

            SectionTitle("Theme")
            ThemeGrid(selected = s.themeId) { id -> update { it.copy(themeId = id) } }

            SectionTitle("Accent")
            AccentRow(s) { color -> update { it.copy(accent = color) } }

            SectionTitle("Messages")
            Label("Layout")
            Segmented(listOf("Cozy", "Compact"), s.density.ordinal) { i ->
                update { it.copy(density = MessageDensity.entries[i]) }
            }
            Label("Avatar shape")
            Segmented(listOf("Circle", "Rounded", "Square"), s.avatarShape.ordinal) { i ->
                update { it.copy(avatarShape = AvatarShape.entries[i]) }
            }
            var scale by remember(s.fontScale) { mutableFloatStateOf(s.fontScale) }
            Label("Text size · ${(scale * 100).roundToInt()}%")
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { update { it.copy(fontScale = scale) } },
                valueRange = 0.85f..1.3f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = p.accent,
                    activeTrackColor = p.accent,
                    inactiveTrackColor = p.elevated,
                    activeTickColor = p.onAccent.copy(alpha = 0.4f),
                    inactiveTickColor = p.muted.copy(alpha = 0.4f),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Toggle("Show avatars", "Cozy layout only", s.showAvatars) { v -> update { it.copy(showAvatars = v) } }
            Toggle("24-hour time", null, s.use24h) { v -> update { it.copy(use24h = v) } }

            SectionTitle("Media")
            Toggle("Image previews", "Show attached images inline", s.showMedia) { v -> update { it.copy(showMedia = v) } }
            Toggle("Link embeds", "Show previews for links and bots", s.showEmbeds) { v -> update { it.copy(showEmbeds = v) } }
            Toggle("Animate GIFs and emoji", "Turn off to save data and battery", s.animateMedia) { v ->
                update { it.copy(animateMedia = v) }
            }

            SectionTitle("Privacy")
            Toggle("Typing indicator", "Let others see when you're typing", s.sendTyping) { v ->
                update { it.copy(sendTyping = v) }
            }
            Toggle("Sync read state", "Mark channels read on your other devices", s.syncReadState) { v ->
                update { it.copy(syncReadState = v) }
            }

            SectionTitle("Account")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(me, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.displayName ?: "…", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    me?.username?.let { Text("@$it", color = p.muted, fontSize = 13.sp) }
                }
                TextButton(onClick = { confirmLogout = true }) {
                    Text("Log out", color = p.danger, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "Hush 1.0 · An unofficial client. Not affiliated with Discord.",
                color = p.muted.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = p.surface,
            title = { Text("Log out?", color = p.text) },
            text = { Text("Your token will be removed from this device.", color = p.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    vm.logout()
                }) { Text("Log out", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel", color = p.text) }
            },
        )
    }
}

@Composable
private fun PreviewCard() {
    val p = LocalPalette.current
    val sample = remember {
        Message(
            id = newNonce(),
            channelId = "0",
            author = User(id = "0", username = "hush", globalName = "Hush"),
            content = "Hey! This is how **messages** look with your theme.\n> Tweak it below — changes apply instantly.",
        )
    }
    Box(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface)
            .border(1.dp, p.divider, RoundedCornerShape(16.dp))
            .padding(bottom = 12.dp),
    ) {
        MessageRow(
            msg = sample,
            authorName = "Hush",
            groupStart = true,
            highlight = false,
            resolver = PreviewResolver,
            replyName = { "" },
            onLongPress = {},
            onOpenImage = {},
            onJumpTo = {},
            onReact = {},
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    val p = LocalPalette.current
    Text(
        text = title.uppercase(),
        color = p.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.text, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp))
}

@Composable
private fun ThemeGrid(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val options = remember(dark) { Themes.options(context, dark) }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { theme ->
                    ThemeCard(theme, theme.id == selected, Modifier.weight(1f)) { onSelect(theme.id) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeCard(t: HushPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val current = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(t.background)
            .border(if (selected) 2.dp else 1.dp, if (selected) current.accent else t.divider, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp)) {
            Box(Modifier.width(10.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(t.rail))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Bar(t.text, 0.65f)
                Bar(t.muted, 0.95f)
                Bar(t.muted, 0.5f)
                Bar(t.accent, 0.35f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = t.name,
                color = t.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = t.accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Bar(color: Color, fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun AccentRow(s: AppSettings, onSelect: (Long?) -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val themeAccent = remember(s.themeId, dark) { Themes.resolve(s.themeId, dark, context).accent }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccentDot(themeAccent, selected = s.accent == null, label = "A") { onSelect(null) }
        ACCENTS.forEach { argb ->
            AccentDot(Color(argb), selected = s.accent == argb, label = null) { onSelect(argb) }
        }
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) p.text else Color.Transparent, CircleShape)
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = onColor, modifier = Modifier.size(16.dp))
            label != null -> Text(label, color = onColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = p.accent.copy(alpha = 0.2f),
                    activeContentColor = p.text,
                    activeBorderColor = p.divider,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = p.muted,
                    inactiveBorderColor = p.divider,
                ),
            ) {
                Text(label, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                checkedBorderColor = p.accent,
                uncheckedThumbColor = p.muted,
                uncheckedTrackColor = p.elevated,
                uncheckedBorderColor = p.divider,
            ),
        )
    }
}
