package app.switchboard.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.switchboard.core.Sb
import app.switchboard.core.Sending
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.chat.openDirectChat
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ListRow
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.SpaceRoom
import org.matrix.rustcomponents.sdk.UserProfile

@Composable
fun NewChatScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = runCatching { Sb.matrix.client?.searchUsers(q, 30u)?.results ?: emptyList() }.getOrDefault(emptyList())
        if (q.startsWith("@") && q.contains(":") && results.none { it.userId == q }) results = listOf(UserProfile(q, null, null, null, null)) + results
        searching = false
    }
    fun start(userId: String) = scope.launch {
        try { nav.openChat(openDirectChat(userId)) } catch (e: Exception) { nav.toast("Couldn't start the chat: ${e.message}") }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("New chat", onBack = { nav.pop() })
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Name or @user:server") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (query.isBlank()) {
            ListRow("New group chat", null, Icons.Outlined.GroupAdd) { nav.push(Screen.NewGroup(false, nav.selectedSpace.value)) }
            ListRow("New space", null, Icons.Outlined.AddCircleOutline) { nav.push(Screen.NewGroup(true)) }
            ListRow("Join a room or space", "By address or link", Icons.Outlined.TravelExplore) { nav.push(Screen.JoinRoom) }
        }
        if (searching) CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.userId }) { u ->
                Row(Modifier.fillMaxWidth().clickable { start(u.userId) }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.avatarUrl, u.displayName ?: u.userId, size = 42.dp, key = u.userId)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(u.displayName ?: u.userId, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text(u.userId, style = Sbt.tiny, color = c.textFaint)
                    }
                }
            }
        }
    }
}

@Composable
fun NewGroupScreen(space: Boolean, parent: String?) {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var invites by remember { mutableStateOf("") }
    var encrypted by remember { mutableStateOf(!space) }
    var public by remember { mutableStateOf(false) }
    var addToParent by remember { mutableStateOf(parent != null && !space) }
    var busy by remember { mutableStateOf(false) }
    val parentName = parent?.let { Sb.matrix.summary(it)?.realName }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(if (space) "New space" else "New group chat", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(topic, { topic = it }, label = { Text("Topic (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(invites, { invites = it }, label = { Text("Invite (optional)") }, placeholder = { Text("@alex:server, @sam:server") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
            }
            if (!space) ToggleRow("End-to-end encryption", "Can't be turned off later", encrypted) { encrypted = it }
            ToggleRow("Public", "Anyone can find and join it", public) { public = it }
            if (parent != null && !space) ToggleRow("Add to $parentName", null, addToParent) { addToParent = it }
            Button(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val client = Sb.matrix.client ?: throw IllegalStateException("Not connected")
                            val people = invites.split(',', ' ', '\n').map { it.trim() }.filter { it.startsWith("@") && it.contains(":") }
                            val id = client.createRoom(
                                CreateRoomParameters(
                                    name = name.trim(), topic = topic.trim().ifEmpty { null }, isEncrypted = encrypted && !space,
                                    isDirect = false, visibility = if (public) RoomVisibility.Public else RoomVisibility.Private,
                                    preset = if (public) RoomPreset.PUBLIC_CHAT else RoomPreset.PRIVATE_CHAT,
                                    invite = people.ifEmpty { null }, isSpace = space,
                                ),
                            )
                            if (addToParent && parent != null) runCatching { client.spaceService().addChildToSpace(id, parent); Sb.matrix.reloadSpace(parent) }
                            nav.pop()
                            if (space) { delay(800); nav.selectedSpace.value = id } else nav.openChat(id)
                        } catch (e: Exception) {
                            nav.toast("Couldn't create it: ${e.message}")
                        }
                        busy = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
            ) { if (busy) CircularProgressIndicator(color = c.onAccent) else Text("Create") }
        }
    }
}

object JoinRoomState { val pending = MutableStateFlow<String?>(null) }

/** Turns an address, ID or matrix.to link into something to join, plus servers to join through. */
fun parseJoinTarget(input: String): Pair<String, List<String>>? {
    var s = input.trim()
    if (s.isEmpty()) return null
    val via = mutableListOf<String>()
    if (s.startsWith("https://matrix.to/#/")) s = s.removePrefix("https://matrix.to/#/")
    if (s.startsWith("matrix:")) {
        // matrix:r/alias:server or matrix:roomid/id:server
        s = s.removePrefix("matrix:")
        s = when {
            s.startsWith("r/") -> "#" + s.removePrefix("r/")
            s.startsWith("roomid/") -> "!" + s.removePrefix("roomid/")
            else -> s
        }
    }
    val q = s.indexOf('?')
    if (q >= 0) {
        s.substring(q + 1).split('&').forEach { p -> if (p.startsWith("via=")) via += java.net.URLDecoder.decode(p.removePrefix("via="), "UTF-8") }
        s = s.substring(0, q)
    }
    s = java.net.URLDecoder.decode(s.substringBefore('/'), "UTF-8")
    if (!s.startsWith("#") && !s.startsWith("!")) s = "#$s"
    if (!s.contains(':')) return null
    return s to via
}

@Composable
fun JoinRoomScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val pending by JoinRoomState.pending.collectAsState()
    var input by remember { mutableStateOf(pending ?: "") }
    var preview by remember { mutableStateOf<org.matrix.rustcomponents.sdk.RoomPreviewInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) { pending?.let { input = it; JoinRoomState.pending.value = null } }
    LaunchedEffect(input) {
        preview = null; error = null
        val t = parseJoinTarget(input) ?: return@LaunchedEffect
        delay(400)
        val client = Sb.matrix.client ?: return@LaunchedEffect
        preview = runCatching {
            if (t.first.startsWith("#")) client.getRoomPreviewFromRoomAlias(t.first).info() else client.getRoomPreviewFromRoomId(t.first, t.second).info()
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Join a room or space", onBack = { nav.pop() })
        OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("Address or link") },
            placeholder = { Text("#room:server or https://matrix.to/#/…") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        preview?.let { p ->
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.avatarUrl, p.name ?: p.canonicalAlias ?: p.roomId, size = 56.dp, key = p.roomId)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.name ?: p.canonicalAlias ?: p.roomId, fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text)
                    Kicker("${p.numJoinedMembers} members")
                    p.topic?.let { Text(it, style = Sbt.small, color = c.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        error?.let { Text(it, color = c.danger, style = Sbt.small, modifier = Modifier.padding(horizontal = 20.dp)) }
        Button(
            enabled = !busy && parseJoinTarget(input) != null,
            onClick = {
                val t = parseJoinTarget(input) ?: return@Button
                busy = true
                scope.launch {
                    try {
                        val room = Sb.matrix.client!!.joinRoomByIdOrAlias(t.first, t.second)
                        val id = room.id()
                        nav.pop()
                        if (room.isSpace()) { delay(800); nav.selectedSpace.value = id } else nav.openChat(id)
                    } catch (e: Exception) { error = "Couldn't join: ${e.message}" }
                    busy = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
        ) { if (busy) CircularProgressIndicator(color = c.onAccent) else Text("Join") }
    }
}

/** Rooms listed in a space, including ones you haven't joined. */
@Composable
fun SpaceRoomsScreen(spaceId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<SpaceRoom>?>(null) }
    var joining by remember { mutableStateOf<String?>(null) }
    val summaries by Sb.matrix.summaries.collectAsState()
    LaunchedEffect(spaceId) {
        rooms = runCatching {
            val list = Sb.matrix.client!!.spaceService().spaceRoomList(spaceId)
            repeat(5) {
                list.paginate()
                val st = list.paginationState()
                if (st is uniffi.matrix_sdk_ui.SpaceRoomListPaginationState.Idle && st.endReached) return@repeat
            }
            list.rooms()
        }.getOrElse { nav.toast("Couldn't load: ${it.message}"); emptyList() }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Rooms in this space", kicker = summaries[spaceId]?.realName, onBack = { nav.pop() })
        val list = rooms
        if (list == null) { CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp)); return@Column }
        if (list.isEmpty()) Text("Nothing listed in this space.", style = Sbt.body, color = c.textMuted, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.roomId }) { r ->
                val joined = r.state == Membership.JOINED || summaries[r.roomId]?.membership == Membership.JOINED
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.avatarUrl, r.displayName, size = 42.dp, key = r.roomId)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.displayName, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.numJoinedMembers} members" + (r.topic?.let { " · $it" } ?: ""), style = Sbt.tiny, color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (joined) TextButton(onClick = { if (summaries[r.roomId]?.isSpace == true) { nav.pop(); nav.selectedSpace.value = r.roomId } else nav.openChat(r.roomId) }) { Text("Open") }
                    else OutlinedButton(enabled = joining == null, onClick = {
                        joining = r.roomId
                        scope.launch {
                            runCatching { Sb.matrix.client!!.joinRoomByIdOrAlias(r.roomId, r.via) }.onFailure { nav.toast("Couldn't join: ${it.message}") }
                            joining = null
                        }
                    }) { if (joining == r.roomId) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), color = c.accent) else Text("Join") }
                }
                Hairline()
            }
        }
    }
}

/** Sending text or files shared from another app. */
@Composable
fun ShareScreen(text: String?, uris: List<Uri>) {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val rooms = remember(summaries, order, query, plus) {
        HomeData.allChats(summaries, order).filter { !it.isInvite && it.canSendMessages && (query.isBlank() || plus.displayName(it).contains(query, true)) }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Share to", kicker = if (uris.isNotEmpty()) "${uris.size} file${if (uris.size > 1) "s" else ""}" else "Text", onBack = { nav.pop() })
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search chats") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(rooms, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable {
                    nav.openChat(s.id)
                    scope.launch {
                        try {
                            val t = Sb.matrix.room(s.id)!!.timeline()
                            if (!text.isNullOrBlank() && uris.isEmpty()) t.send(Sending.content(text, emptyList()))
                            for (u in uris) Sending.sendFile(context, t, Sending.copyToCache(context, u), null, if (uris.size == 1) text else null)
                        } catch (e: Exception) { nav.toast("Couldn't share: ${e.message}") }
                    }
                }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.avatarUrl, plus.displayName(s), size = 42.dp, key = s.id)
                    Spacer(Modifier.width(14.dp))
                    Text(plus.displayName(s), fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text)
                }
            }
        }
    }
}

@Composable
fun EditRoomScreen(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Sb.matrix.summary(roomId)
    var name by remember { mutableStateOf(s?.realName ?: "") }
    var topic by remember { mutableStateOf(s?.topic ?: "") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try {
                val lf = Sending.copyToCache(context, uri)
                Sb.matrix.room(roomId)?.uploadAvatar(lf.mime, lf.file.readBytes(), ImageInfo(null, null, lf.mime, lf.size.toULong(), null, null, null, null))
                nav.toast("Picture updated")
            } catch (e: Exception) { nav.toast("Couldn't update the picture: ${e.message}") }
        }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Edit", kicker = s?.realName, onBack = { nav.pop() })
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(s?.avatarUrl, name, size = 64.dp, key = roomId)
                Spacer(Modifier.width(14.dp))
                Text("Change picture", color = c.accent, fontFamily = Inter, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(topic, { topic = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val r = Sb.matrix.room(roomId)
                        runCatching {
                            if (name.trim() != s?.realName) r?.setName(name.trim())
                            if (topic.trim() != (s?.topic ?: "")) r?.setTopic(topic.trim())
                        }.onSuccess { nav.pop() }.onFailure { nav.toast("Couldn't save: ${it.message}") }
                        busy = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Save") }
        }
    }
}

@Composable
fun InvitesScreen(spaceId: String?) {
    val c = Sbt.c
    val nav = LocalNav.current
    val summaries by Sb.matrix.summaries.collectAsState()
    val invites = summaries.values.filter { it.isInvite }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Invites", onBack = { nav.pop() })
        LazyColumn {
            items(invites, key = { it.id }) { s ->
                ListRow(s.realName, "From ${s.inviterName ?: "someone"}") { nav.openChat(s.id) }
            }
        }
        if (spaceId != null && invites.isEmpty()) Text("No invites.", color = c.textMuted, modifier = Modifier.padding(20.dp))
    }
}
