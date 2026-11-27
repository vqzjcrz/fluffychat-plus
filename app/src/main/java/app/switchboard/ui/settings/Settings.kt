package app.switchboard.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.BuildConfig
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.core.Sending
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ListRow
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.common.SectionLabel
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import app.switchboard.ui.theme.Themes
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.VerificationState

@Composable
fun SettingsScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by Sb.matrix.profile.collectAsState()
    val recovery by Sb.matrix.recovery.collectAsState()
    val verification by Sb.matrix.verification.collectAsState()
    var editName by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try {
                val lf = Sending.copyToCache(context, uri)
                Sb.matrix.client?.uploadAvatar(lf.mime, lf.file.readBytes())
                nav.toast("Profile picture updated")
            } catch (e: Exception) { nav.toast("Couldn't update the picture: ${e.message}") }
        }
    }
    val userId = Sb.matrix.userId ?: ""
    val name = profile?.displayName ?: userId.removePrefix("@").substringBefore(':')

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Settings", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Avatar(profile?.avatarUrl, name, size = 76.dp, key = userId)
                    Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(Icons.Filled.CameraAlt, "Change picture", tint = c.onAccent, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f).clickable { editName = true }) {
                    Kicker("Your profile")
                    Text(name, fontFamily = app.switchboard.ui.theme.InterDisplay, fontWeight = FontWeight.Black, fontSize = 26.sp, color = c.text, maxLines = 1)
                    Text(userId, style = Sbt.small, color = c.textMuted, maxLines = 1)
                }
            }
            Hairline()
            if (recovery != RecoveryState.ENABLED || verification == VerificationState.UNVERIFIED) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.12f))
                        .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable { nav.push(Screen.SettingsSecurity) }.padding(16.dp),
                ) {
                    Text(
                        if (verification == VerificationState.UNVERIFIED) "Verify this device" else "Set up key backup",
                        fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text,
                    )
                    Text(
                        if (verification == VerificationState.UNVERIFIED) "Enter your recovery key or confirm from another device to read your encrypted messages."
                        else "Keep a recovery key so your encrypted messages survive a reinstall.",
                        style = Sbt.small, color = c.textMuted,
                    )
                }
            }
            ListRow("Style", "Theme and colors", Icons.Outlined.Palette) { nav.push(Screen.SettingsStyle) }
            ListRow("Chats", "Swipes, read receipts, message box", Icons.AutoMirrored.Outlined.Chat) { nav.push(Screen.SettingsChat) }
            ListRow("Notifications", null, Icons.Outlined.Notifications) { nav.push(Screen.SettingsNotifications) }
            ListRow("Security and encryption", null, Icons.Outlined.Security) { nav.push(Screen.SettingsSecurity) }
            ListRow("Devices", null, Icons.Outlined.Devices) { nav.push(Screen.SettingsDevices) }
            ListRow("Ignored people", null, Icons.Outlined.Block) { nav.push(Screen.SettingsIgnored) }
            ListRow("About Switchboard", "Version ${BuildConfig.VERSION_NAME}", Icons.Outlined.Info) { nav.push(Screen.SettingsAbout) }
            Hairline()
            ListRow("Log out", null, Icons.AutoMirrored.Outlined.Logout, danger = true) { logout = true }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (editName) {
        TextInputDialog("Display name", initial = name, onDismiss = { editName = false }) { n ->
            scope.launch { runCatching { Sb.matrix.client?.setDisplayName(n.trim()) }.onFailure { nav.toast("Couldn't change your name: ${it.message}") } }
        }
    }
    if (logout) {
        ConfirmDialog(
            "Log out?",
            if (recovery == RecoveryState.ENABLED) "You can log back in any time. Your recovery key unlocks your encrypted messages again."
            else "You haven't set up key backup. You may lose access to your encrypted messages.",
            "Log out", danger = true, onDismiss = { logout = false },
        ) { scope.launch { Sb.matrix.logout(); nav.home() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsStyleScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val theme by Prefs.theme.collectAsState()
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Style", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("App theme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Themes.presets.forEach { p ->
                    ThemeCard(p.name, p.colors.background, p.colors.bubbleIn, p.colors.ownBubble, p.colors.accent, p.colors.text, theme == p.id) { Prefs.setTheme(p.id) }
                }
                ThemeCard("Material You", Color(0xFF1D1B20), Color(0xFF2B2930), Color(0xFF4F378B), Color(0xFFD0BCFF), Color.White, theme == Themes.MATERIAL_YOU) {
                    Prefs.setTheme(Themes.MATERIAL_YOU)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Spaces can have their own color too: open a space, tap ⋯ and choose Space color. It changes the accents only, so text stays readable.",
                style = Sbt.small, color = c.textMuted,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ThemeCard(name: String, bg: Color, bubble: Color, own: Color, accent: Color, text: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Sbt.c
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().height(96.dp).background(bg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(52.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(bubble))
            Box(Modifier.align(Alignment.End).width(46.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(own))
            Box(Modifier.width(34.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(bubble))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width(30.dp).height(4.dp).background(text.copy(alpha = 0.6f)))
            }
        }
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
            if (selected) androidx.compose.material3.Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Sbt.c
    ListRow(title, subtitle, trailing = {
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent))
    }) { onChange(!checked) }
}

@Composable
fun SettingsChatScreen() {
    val nav = LocalNav.current
    val swipe by Prefs.swipeToReplyLeftToRight.collectAsState()
    val receipts by Prefs.showReadReceipts.collectAsState()
    val enter by Prefs.sendOnEnter.collectAsState()
    val hide by Prefs.hideStateEvents.collectAsState()
    val gifs by Prefs.autoplayGifs.collectAsState()
    Column(Modifier.fillMaxSize().background(Sbt.c.background)) {
        ScreenHeader("Chats", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ToggleRow(
                "Swipe left-to-right to reply",
                if (swipe) "Replying swipes right. The spaces panel swipe is off." else "Swipe right-to-left to reply. Swipe left-to-right opens the spaces panel.",
                swipe,
            ) { Prefs.setSwipeToReplyLeftToRight(it) }
            ToggleRow("Show read receipts", "\"Read\" under your messages", receipts) { Prefs.setShowReadReceipts(it) }
            ToggleRow("Enter key sends", null, enter) { Prefs.setSendOnEnter(it) }
            ToggleRow("Hide joins, leaves and other changes", null, hide) { Prefs.setHideStateEvents(it) }
            ToggleRow("Play GIFs automatically", null, gifs) { Prefs.setAutoplayGifs(it) }
        }
    }
}

@Composable
fun SettingsAboutScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("About", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(app.switchboard.R.drawable.ic_launcher_foreground), null,
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black),
            )
            Spacer(Modifier.height(16.dp))
            Text("Switchboard", style = Sbt.title, color = c.text)
            Text("Version ${BuildConfig.VERSION_NAME}", style = Sbt.body, color = c.textMuted)
            Spacer(Modifier.height(16.dp))
            Text("A Matrix client for everything you bridge into one place: Discord, Signal, Instagram, WhatsApp and the rest.", style = Sbt.body, color = c.text)
            SectionLabel("Built with")
            listOf(
                "matrix-rust-sdk (Apache License 2.0), The Matrix.org Foundation",
                "Jetpack Compose, Media3, Emoji2 (Apache License 2.0), Google",
                "Coil (Apache License 2.0)",
                "Reorderable (Apache License 2.0), Calvin Liang",
                "UnifiedPush connector (Apache License 2.0)",
                "Inter typeface (SIL Open Font License 1.1), Rasmus Andersson",
            ).forEach { Text("• $it", style = Sbt.small, color = c.textMuted, modifier = Modifier.padding(vertical = 3.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(
                "Source code",
                style = Sbt.body, color = c.accent,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/vqzjcrz/fluffychat-plus/tree/switchboard")))
                },
            )
        }
    }
}

fun openAppNotificationSettings(context: android.content.Context) {
    val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(i) }
}
