package app.switchboard.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.switchboard.core.Api
import app.switchboard.core.ApiException
import app.switchboard.core.Sb
import app.switchboard.push.PushSetup
import app.switchboard.ui.LocalNav
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ListRow
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.common.SectionLabel
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsNotificationsScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by PushSetup.status.collectAsState()
    var distributors by remember { mutableStateOf(PushSetup.distributors(context)) }
    var pick by remember { mutableStateOf(false) }
    var userMention by remember { mutableStateOf<Boolean?>(null) }
    var roomMention by remember { mutableStateOf<Boolean?>(null) }
    var invites by remember { mutableStateOf<Boolean?>(null) }
    var calls by remember { mutableStateOf<Boolean?>(null) }
    var groupMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var dmMode by remember { mutableStateOf<RoomNotificationMode?>(null) }
    var modeSheet by remember { mutableStateOf<Boolean?>(null) } // true = DMs, false = groups

    LaunchedEffect(Unit) {
        PushSetup.refreshStatus(context)
        distributors = PushSetup.distributors(context)
        runCatching {
            val n = Sb.matrix.notificationSettings()
            userMention = n.isUserMentionEnabled(); roomMention = n.isRoomMentionEnabled()
            invites = n.isInviteForMeEnabled(); calls = n.isCallEnabled()
            groupMode = n.getDefaultRoomNotificationMode(false, false)
            dmMode = n.getDefaultRoomNotificationMode(false, true)
        }
    }
    fun label(m: RoomNotificationMode?) = when (m) {
        RoomNotificationMode.ALL_MESSAGES -> "All messages"
        RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY -> "Mentions only"
        RoomNotificationMode.MUTE -> "Off"
        null -> "…"
    }
    fun appName(pkg: String?) = pkg?.let {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Notifications", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp).background(c.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Kicker("Push")
                Spacer(Modifier.height(4.dp))
                when {
                    status.endpoint != null -> {
                        Text("On, through ${appName(status.distributor) ?: "your push app"}", fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text)
                        Text("Gateway: ${status.gateway}", style = Sbt.tiny, color = c.textMuted)
                    }
                    distributors.isEmpty() -> {
                        Text("No push app installed", fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text)
                        Text("Install ntfy (or another UnifiedPush app) to get notifications while Switchboard is closed. With ntfy, point it at your own server or ntfy.sh.", style = Sbt.small, color = c.textMuted)
                    }
                    else -> {
                        Text("Not set up yet", fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text)
                        Text("Pick the app that should deliver Switchboard's notifications.", style = Sbt.small, color = c.textMuted)
                    }
                }
                status.error?.let { Text("Error: $it", style = Sbt.tiny, color = c.danger) }
                Row {
                    if (distributors.isNotEmpty()) TextButton(onClick = { pick = true }) { Text(if (status.endpoint != null) "Change push app" else "Pick push app") }
                    if (distributors.isNotEmpty()) TextButton(onClick = {
                        (context as? Activity)?.let { a -> PushSetup.useDefault(a) { ok -> if (!ok) nav.toast("Pick one from the list") } } ?: PushSetup.ensureRegistered(context)
                    }) { Text("Register again") }
                }
            }
            SectionLabel("Notify me about")
            userMention?.let { v -> ToggleRow("Mentions of my name", null, v) { n -> userMention = n; scope.launch { runCatching { Sb.matrix.notificationSettings().setUserMentionEnabled(n) } } } }
            roomMention?.let { v -> ToggleRow("@room mentions", null, v) { n -> roomMention = n; scope.launch { runCatching { Sb.matrix.notificationSettings().setRoomMentionEnabled(n) } } } }
            invites?.let { v -> ToggleRow("Invites", null, v) { n -> invites = n; scope.launch { runCatching { Sb.matrix.notificationSettings().setInviteForMeEnabled(n) } } } }
            calls?.let { v -> ToggleRow("Calls", null, v) { n -> calls = n; scope.launch { runCatching { Sb.matrix.notificationSettings().setCallEnabled(n) } } } }
            SectionLabel("Default for chats")
            ListRow("Direct messages", label(dmMode), Icons.Outlined.NotificationsActive) { modeSheet = true }
            ListRow("Group chats", label(groupMode), Icons.Outlined.NotificationsActive) { modeSheet = false }
            Hairline()
            ListRow("Android notification settings", "Sounds, vibration and how they look", Icons.Outlined.Settings) { openAppNotificationSettings(context) }
            Text(
                "Snoozed chats don't notify until the snooze ends. Reels go only to the reels chat when the reels hub's quiet rule is on.",
                style = Sbt.small, color = c.textMuted, modifier = Modifier.padding(20.dp),
            )
        }
    }
    if (pick) {
        ActionSheet(
            title = "Push app",
            actions = distributors.map { d -> SheetAction(appName(d) ?: d, subtitle = d) { PushSetup.choose(context, d) } },
            onDismiss = { pick = false },
        )
    }
    modeSheet?.let { dm ->
        fun set(m: RoomNotificationMode) = scope.launch {
            runCatching {
                val n = Sb.matrix.notificationSettings()
                n.setDefaultRoomNotificationMode(false, dm, m)
                n.setDefaultRoomNotificationMode(true, dm, m)
                if (dm) dmMode = m else groupMode = m
            }.onFailure { nav.toast("Couldn't change: ${it.message}") }
        }
        ActionSheet(
            title = if (dm) "Direct messages" else "Group chats",
            actions = listOf(
                SheetAction("All messages") { set(RoomNotificationMode.ALL_MESSAGES) },
                SheetAction("Mentions only") { set(RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY) },
                SheetAction("Off") { set(RoomNotificationMode.MUTE) },
            ),
            onDismiss = { modeSheet = null },
        )
    }
}

private data class Device(val id: String, val name: String?, val ip: String?, val lastSeen: Long)

@Composable
fun SettingsDevicesScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<Device>?>(null) }
    var rename by remember { mutableStateOf<Device?>(null) }
    var remove by remember { mutableStateOf<Device?>(null) }
    val own = Sb.matrix.stored?.session?.deviceId
    suspend fun load() {
        devices = runCatching {
            val arr = Api.get("/_matrix/client/v3/devices").optJSONArray("devices") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Device(o.getString("device_id"), o.optString("display_name").ifEmpty { null }, o.optString("last_seen_ip").ifEmpty { null }, o.optLong("last_seen_ts"))
            }.sortedWith(compareByDescending<Device> { it.id == own }.thenByDescending { it.lastSeen })
        }.getOrElse { nav.toast("Couldn't load devices: ${it.message}"); emptyList() }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Devices", onBack = { nav.pop() })
        val list = devices
        if (list == null) { CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp)); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, null, tint = if (d.id == own) c.accent else c.textMuted)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name ?: d.id, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text(
                            listOfNotNull(if (d.id == own) "This device" else null, d.id, d.ip,
                                if (d.lastSeen > 0) SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(d.lastSeen)) else null).joinToString(" · "),
                            style = Sbt.tiny, color = c.textMuted,
                        )
                    }
                    IconButton(onClick = { rename = d }) { Icon(Icons.Outlined.Edit, "Rename", tint = c.textMuted) }
                    if (d.id != own) IconButton(onClick = { remove = d }) { Icon(Icons.Outlined.Delete, "Log out", tint = c.danger) }
                }
                Hairline()
            }
        }
    }
    rename?.let { d ->
        TextInputDialog("Device name", initial = d.name ?: "", onDismiss = { rename = null }) { name ->
            scope.launch {
                runCatching { Api.put("/_matrix/client/v3/devices/${Api.enc(d.id)}", JSONObject().put("display_name", name.trim())) }
                    .onFailure { nav.toast("Couldn't rename: ${it.message}") }
                load()
            }
        }
    }
    remove?.let { d ->
        androidx.compose.runtime.key(d.id) {
            var password by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { remove = null },
                title = { Text("Log out ${d.name ?: d.id}?") },
                text = {
                    Column {
                        Text("Enter your password to confirm.", style = Sbt.small)
                        androidx.compose.material3.OutlinedTextField(password, { password = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        remove = null
                        scope.launch {
                            runCatching { deleteDevice(d.id, password) }.onFailure { nav.toast("Couldn't log it out: ${it.message}") }
                            load()
                        }
                    }) { Text("Log out", color = c.danger) }
                },
                dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } },
            )
        }
    }
}

private suspend fun deleteDevice(id: String, password: String) {
    val body = JSONObject().put("devices", JSONArray().put(id))
    try {
        Api.post("/_matrix/client/v3/delete_devices", body)
    } catch (e: ApiException) {
        if (e.status != 401) throw e
        val session = JSONObject(e.body).optString("session")
        body.put("auth", JSONObject()
            .put("type", "m.login.password")
            .put("identifier", JSONObject().put("type", "m.id.user").put("user", Sb.matrix.userId))
            .put("password", password)
            .put("session", session))
        Api.post("/_matrix/client/v3/delete_devices", body)
    }
}

@Composable
fun SettingsIgnoredScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { list = runCatching { Sb.matrix.client?.ignoredUsers() ?: emptyList() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Ignored people", onBack = { nav.pop() })
        val l = list ?: return@Column
        if (l.isEmpty()) Text("You're not ignoring anyone.", style = Sbt.body, color = c.textMuted, modifier = Modifier.padding(20.dp))
        LazyColumn {
            items(l, key = { it }) { u ->
                ListRow(u, null, trailing = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { Sb.matrix.client?.unignoreUser(u) }
                            list = runCatching { Sb.matrix.client?.ignoredUsers() ?: emptyList() }.getOrDefault(emptyList())
                        }
                    }) { Text("Stop ignoring") }
                })
            }
        }
    }
}
