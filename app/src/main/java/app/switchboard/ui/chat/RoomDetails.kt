package app.switchboard.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Bridges
import app.switchboard.core.PlusEdit
import app.switchboard.core.RoomActions
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ListRow
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.common.SectionLabel
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.home.LocalRoomUi
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.RoomMember
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility

@Composable
fun RoomDetailsScreen(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaries by Sb.matrix.summaries.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    val s = summaries[roomId]
    if (s == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var notifSheet by remember { mutableStateOf(false) }
    var canEdit by remember { mutableStateOf(false) }
    LaunchedEffect(roomId) {
        canEdit = runCatching {
            Sb.matrix.room(roomId)?.roomInfo()?.powerLevels?.canOwnUserSendState(uniffi.ruma_events.StateEventType.RoomName) == true
        }.getOrDefault(false)
    }
    val name = plus.displayName(s)
    val bridge = Bridges.get(roomId)?.name ?: Bridges.guess(s.heroIds)
    val readOnly = HomeData.isReadOnly(s, plus)

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(if (s.isSpace) "Space details" else "Chat details", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(s.avatarUrl, name, size = 104.dp, key = roomId)
                Spacer(Modifier.height(16.dp))
                Text(name, style = Sbt.title.copy(fontSize = 26.sp), color = c.text, textAlign = TextAlign.Center)
                if (plus.nickname(roomId) != null) Text("Real name: ${s.realName}", style = Sbt.small, color = c.textMuted)
                Spacer(Modifier.height(4.dp))
                Kicker(listOfNotNull(bridge, "${s.joinedCount} members", if (s.isEncrypted) "Encrypted" else null).joinToString(" · "))
                s.topic?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = Sbt.body, color = c.textMuted, textAlign = TextAlign.Center)
                }
            }
            Hairline()
            if (!s.isSpace) {
                val mode = when (s.notificationMode) {
                    RoomNotificationMode.ALL_MESSAGES -> "All messages"
                    RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY -> "Mentions only"
                    RoomNotificationMode.MUTE -> "Muted"
                    null -> "Default"
                }
                ListRow("Notifications", mode, Icons.Outlined.Notifications) { notifSheet = true }
                val snoozed = plus.snoozedUntil(roomId)
                ListRow(if (snoozed != null) "Snoozed until ${whenText(snoozed)}" else "Snooze", if (snoozed != null) "Tap to end the snooze" else null, Icons.Outlined.Bedtime) {
                    if (snoozed != null) scope.launch { PlusEdit.setSnooze(roomId, null) } else ui.snoozeFor = listOf(roomId)
                }
                if (s.canSendMessages) {
                    ListRow(if (readOnly) "Read-only" else "Posting allowed", if (readOnly) "Tap to allow posting" else "Tap to make read-only",
                        if (readOnly) Icons.Outlined.Lock else Icons.Outlined.LockOpen) { scope.launch { RoomActions.setReadOnly(s, !readOnly) } }
                }
            }
            ListRow("Rename for me", plus.nickname(roomId), Icons.Outlined.DriveFileRenameOutline) { ui.renameFor = s }
            if (canEdit) ListRow("Edit name, topic and picture", null, Icons.Outlined.Edit) { nav.push(Screen.EditRoom(roomId)) }
            ListRow("Members", "${s.joinedCount}", Icons.Outlined.Group) { nav.push(Screen.Members(roomId)) }
            ListRow("Media", null, Icons.Outlined.PhotoLibrary) { nav.push(Screen.SpaceMedia(roomId)) }
            ListRow("Copy link", s.alias ?: roomId, Icons.Outlined.ContentCopy) {
                scope.launch { runCatching { copyText(context, Sb.matrix.room(roomId)!!.matrixToPermalink()); nav.toast("Link copied") } }
            }
            ListRow("Encryption", if (s.isEncrypted) "Messages are end-to-end encrypted" else "Not encrypted", Icons.Outlined.Shield)
            ListRow("Leave", null, Icons.AutoMirrored.Outlined.ExitToApp, danger = true) { ui.leaveFor = listOf(roomId) }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (notifSheet) {
        fun set(m: RoomNotificationMode?) = scope.launch {
            runCatching { Sb.matrix.setNotificationMode(roomId, m); Sb.matrix.refreshRooms(listOf(roomId)) }.onFailure { nav.toast("Couldn't change: ${it.message}") }
        }
        ActionSheet(
            title = "Notifications",
            actions = listOf(
                SheetAction("Default") { set(null) },
                SheetAction("All messages") { set(RoomNotificationMode.ALL_MESSAGES) },
                SheetAction("Mentions only") { set(RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY) },
                SheetAction("Mute") { set(RoomNotificationMode.MUTE) },
            ),
            onDismiss = { notifSheet = false },
        )
    }
}

@Composable
fun MembersScreen(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Members", onBack = { nav.pop() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(invite, { invite = it }, singleLine = true, placeholder = { Text("@someone:server to invite") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(enabled = !busy && invite.trim().startsWith("@") && invite.contains(":"), onClick = {
                busy = true
                scope.launch {
                    runCatching { Sb.matrix.room(roomId)?.inviteUserById(invite.trim()) }
                        .onSuccess { nav.toast("Invited ${invite.trim()}"); invite = "" }
                        .onFailure { nav.toast("Couldn't invite: ${it.message}") }
                    busy = false
                }
            }) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Invite") }
        }
        MemberList(roomId)
    }
}

@Composable
fun MemberList(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    var members by remember(roomId) { mutableStateOf<List<RoomMember>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(roomId) {
        members = try {
            val room = Sb.matrix.room(roomId) ?: throw IllegalStateException()
            val it = runCatching { room.members() }.getOrElse { room.membersNoSync() }
            val out = ArrayList<RoomMember>()
            while (true) { val chunk = it.nextChunk(500u) ?: break; if (chunk.isEmpty()) break; out.addAll(chunk) }
            out.filter { m -> m.membership is MembershipState.Join || m.membership is MembershipState.Invite }
                .sortedWith(compareByDescending<RoomMember> { levelOf(it) }.thenBy { (it.displayName ?: it.userId).lowercase() })
        } catch (_: Exception) { emptyList() }
    }
    val list = members
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        return
    }
    val shown = if (query.isBlank()) list else list.filter { (it.displayName ?: "").contains(query, true) || it.userId.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize()) {
        if (list.size > 15) item {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search ${list.size} members") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        }
        items(shown, key = { it.userId }) { m ->
            Row(
                Modifier.fillMaxWidth().clickable { nav.push(Screen.UserProfile(m.userId, roomId)) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(m.avatarUrl, m.displayName ?: m.userId, size = 40.dp, key = m.userId)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayName ?: m.userId, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.userId + if (m.membership is MembershipState.Invite) " · invited" else "", style = Sbt.tiny, color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val role = roleLabel(m)
                if (role != null) Kicker(role, color = c.accent)
            }
        }
    }
}

fun levelOf(m: RoomMember): Long = when (val p = m.powerLevel) {
    is org.matrix.rustcomponents.sdk.PowerLevel.Value -> p.value
    else -> Long.MAX_VALUE
}

private fun roleLabel(m: RoomMember): String? {
    val n = levelOf(m)
    return when {
        n >= 100 -> "Admin"
        n >= 50 -> "Mod"
        else -> null
    }
}

@Composable
fun UserProfileScreen(userId: String, roomId: String?) {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var member by remember { mutableStateOf<RoomMember?>(null) }
    var ignored by remember { mutableStateOf(false) }
    var canKick by remember { mutableStateOf(false) }
    var canBan by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) {
        val client = Sb.matrix.client
        runCatching { client?.getProfile(userId) }.getOrNull()?.let { name = it.displayName; avatar = it.avatarUrl }
        if (roomId != null) {
            val room = Sb.matrix.room(roomId)
            member = runCatching { room?.member(userId) }.getOrNull()
            member?.let { if (name == null) name = it.displayName; if (avatar == null) avatar = it.avatarUrl }
            val pl = runCatching { room?.roomInfo()?.powerLevels }.getOrNull()
            canKick = runCatching { pl?.canOwnUserKick() == true }.getOrDefault(false)
            canBan = runCatching { pl?.canOwnUserBan() == true }.getOrDefault(false)
        }
        ignored = runCatching { client?.ignoredUsers()?.contains(userId) == true }.getOrDefault(false)
    }
    val own = userId == Sb.matrix.userId
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Profile", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(avatar, name ?: userId, size = 104.dp, key = userId)
                Spacer(Modifier.height(16.dp))
                Text(name ?: userId.removePrefix("@").substringBefore(':'), style = Sbt.title.copy(fontSize = 26.sp), color = c.text, textAlign = TextAlign.Center)
                Text(userId, style = Sbt.small, color = c.textMuted)
                Bridges.guess(listOf(userId))?.let { Spacer(Modifier.height(4.dp)); Kicker("$it user") }
            }
            Hairline()
            if (!own) {
                ListRow("Send a message", null, Icons.AutoMirrored.Outlined.Chat) {
                    scope.launch {
                        try { nav.openChat(openDirectChat(userId)) } catch (e: Exception) { nav.toast("Couldn't start the chat: ${e.message}") }
                    }
                }
            }
            ListRow("Copy user ID", null, Icons.Outlined.ContentCopy) { copyText(context, userId); nav.toast("Copied") }
            if (!own && roomId != null && member != null) {
                if (canKick) ListRow("Remove from chat", null, Icons.Outlined.PersonRemove, danger = true) { confirm = "kick" }
                if (canBan) ListRow("Ban from chat", null, Icons.Outlined.Block, danger = true) { confirm = "ban" }
            }
            if (!own) {
                ListRow(if (ignored) "Stop ignoring" else "Ignore", if (ignored) null else "Hide all messages and invites from this person", Icons.Outlined.Block, danger = !ignored) {
                    scope.launch {
                        runCatching { if (ignored) Sb.matrix.client?.unignoreUser(userId) else Sb.matrix.client?.ignoreUser(userId) }
                            .onSuccess { ignored = !ignored }.onFailure { nav.toast("Couldn't change: ${it.message}") }
                    }
                }
            }
        }
    }
    confirm?.let { what ->
        TextInputDialog(
            title = if (what == "ban") "Ban ${name ?: userId}?" else "Remove ${name ?: userId}?",
            hint = "Reason (optional)", confirm = if (what == "ban") "Ban" else "Remove", allowEmpty = true,
            onDismiss = { confirm = null },
        ) { reason ->
            scope.launch {
                val room = Sb.matrix.room(roomId!!) ?: return@launch
                runCatching { if (what == "ban") room.banUser(userId, reason.ifBlank { null }) else room.kickUser(userId, reason.ifBlank { null }) }
                    .onFailure { nav.toast("Couldn't do that: ${it.message}") }
            }
        }
    }
}

/** Opens the existing direct chat with someone, or starts one. */
suspend fun openDirectChat(userId: String): String {
    val client = Sb.matrix.client ?: throw IllegalStateException("Not connected")
    client.getDmRoom(userId)?.let { return it.id() }
    val bridged = Bridges.guess(listOf(userId)) != null
    return client.createRoom(
        CreateRoomParameters(
            name = null, isEncrypted = !bridged, isDirect = true, visibility = RoomVisibility.Private,
            preset = RoomPreset.TRUSTED_PRIVATE_CHAT, invite = listOf(userId),
        ),
    )
}

