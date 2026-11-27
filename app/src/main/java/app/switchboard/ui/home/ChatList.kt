package app.switchboard.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.RoomActions
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.CircleButton
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TabRowPlain
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.InterDisplay
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch

@Composable
fun ChatListPane() {
    val c = Sbt.c
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val scope = rememberCoroutineScope()
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    val loaded by Sb.matrix.loaded.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val all = remember(summaries, order) { HomeData.allChats(summaries, order) }
    val shown = remember(all, tab, query, plus) {
        val q = query.trim().lowercase()
        all.filter { s ->
            when (tab) {
                1 -> s.isUnread || s.isInvite
                2 -> s.isDirect
                3 -> !s.isDirect
                else -> true
            } && (q.isEmpty() || plus.displayName(s).lowercase().contains(q) || s.realName.lowercase().contains(q))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            if (ui.selecting) {
                SelectionBar(ui, spaceId = null, allIds = shown.map { it.id })
            } else {
                Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Kicker("Switchboard")
                            if (searching) SearchField(query, { query = it }, "Search chats")
                            else Text("Chats", style = Sbt.title, color = c.text)
                        }
                        CircleButton(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search") {
                            searching = !searching; if (!searching) query = ""
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TabRowPlain(listOf("All", "Unread", "People", "Groups"), tab, { tab = it })
                }
            }
            Hairline()
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(if (query.isNotBlank()) "No chats match \"$query\"." else if (tab == 1) "You're all caught up." else "No chats yet.", style = Sbt.body, color = c.textMuted)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.id }) { s ->
                        SwipeActions(
                            unread = s.isUnread,
                            pinned = false,
                            onToggleRead = { scope.launch { runCatching { if (s.isUnread) RoomActions.markRead(s.id) else RoomActions.markUnread(s.id) } } },
                            onPin = null,
                            enabled = !ui.selecting && !s.isInvite,
                        ) {
                            ChatRow(
                                s, plus, selected = s.id in ui.selection,
                                onClick = { if (ui.selecting) ui.toggle(s.id) else nav.openChat(s.id) },
                                onLongClick = { if (ui.selecting) ui.toggle(s.id) else ui.roomMenu = s to null },
                            )
                        }
                        Hairline(Modifier.padding(start = 82.dp))
                    }
                }
            }
        }
        if (!ui.selecting) {
            FloatingActionButton(
                onClick = { nav.push(Screen.NewChat) },
                containerColor = c.accent, contentColor = c.onAccent, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(64.dp),
            ) { Icon(Icons.Filled.Edit, "New chat") }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val c = Sbt.c
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (value.isEmpty()) Text(hint, fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = c.textFaint)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Top bar while selecting several chats. */
@Composable
fun SelectionBar(ui: RoomUi, spaceId: String?, allIds: List<String>) {
    val c = Sbt.c
    val scope = rememberCoroutineScope()
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { ui.endSelection() }) { Icon(Icons.Filled.Close, "Cancel", tint = c.text) }
        Text("${ui.selection.size} selected", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.text, modifier = Modifier.weight(1f))
        IconButton(onClick = { ui.selection.clear(); ui.selection.addAll(allIds) }) { Icon(Icons.Outlined.SelectAll, "Select all", tint = c.text) }
        IconButton(onClick = { ui.moveFor = MoveRequest(ui.selection.toList(), spaceId) }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Move to space", tint = c.text) }
        IconButton(onClick = { ui.leaveFor = ui.selection.toList() }) { Icon(Icons.AutoMirrored.Outlined.ExitToApp, "Leave", tint = c.danger) }
        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More", tint = c.text) }
    }
    if (more) {
        val ids = ui.selection.toList()
        val actions = mutableListOf(
            SheetAction("Mark as read", Icons.Outlined.MarkChatRead) { ui.endSelection(); scope.launch { RoomActions.bulkMarkRead(ids) } },
            SheetAction("Mute", Icons.Outlined.NotificationsOff) { ui.endSelection(); scope.launch { RoomActions.bulkMute(ids, true) } },
            SheetAction("Unmute", Icons.Outlined.Notifications) { ui.endSelection(); scope.launch { RoomActions.bulkMute(ids, false) } },
        )
        if (spaceId != null) actions += SheetAction("Move to category", Icons.AutoMirrored.Outlined.Label) { ui.categoryFor = CategoryRequest(spaceId, ids) }
        ActionSheet(title = "${ids.size} chats", actions = actions, onDismiss = { more = false })
    }
}

