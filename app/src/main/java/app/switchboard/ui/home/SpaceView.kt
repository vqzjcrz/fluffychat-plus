package app.switchboard.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Bridges
import app.switchboard.core.Plus
import app.switchboard.core.PlusEdit
import app.switchboard.core.Prefs
import app.switchboard.core.RoomActions
import app.switchboard.core.RoomSummary
import app.switchboard.core.Sb
import app.switchboard.core.SortMode
import app.switchboard.core.SpaceCategory
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.chat.MemberList
import app.switchboard.ui.chat.SpaceMediaGrid
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.CircleButton
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TabRowPlain
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SpaceView(spaceId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val scope = rememberCoroutineScope()
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val children by Sb.matrix.spaceChildren.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    val bridges by Bridges.info.collectAsState()
    val space = summaries[spaceId] ?: return
    var tab by rememberSaveable(spaceId) { mutableIntStateOf(0) }
    var searching by rememberSaveable(spaceId) { mutableStateOf(false) }
    var query by rememberSaveable(spaceId) { mutableStateOf("") }
    var mutedOpen by rememberSaveable(spaceId) { mutableStateOf(false) }
    var reordering by remember(spaceId) { mutableStateOf(false) }
    var spaceMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<SpaceCategory?>(null) }
    var renameCategory by remember { mutableStateOf<SpaceCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<SpaceCategory?>(null) }
    var leaveSpace by remember { mutableStateOf(false) }

    val layout = remember(spaceId, summaries, order, children, plus, query, bridges) {
        HomeData.spaceLayout(spaceId, summaries, order, children, plus, query)
    }
    val bridge = bridges[spaceId]?.name ?: layout.all.firstNotNullOfOrNull { bridges[it.id]?.name }
    val isDiscord = bridge?.contains("discord", true) == true
    val kicker = listOfNotNull(bridge ?: "Space", "${space.joinedCount} members").joinToString(" · ")

    Column(Modifier.fillMaxSize().background(c.background)) {
        if (ui.selecting) {
            SelectionBar(ui, spaceId, layout.all.map { it.id })
        } else {
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(kicker)
                        if (searching) SearchField(query, { query = it }, "Search ${space.realName}")
                        else Text(space.realName, style = Sbt.title.copy(fontSize = 30.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CircleButton(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search") {
                        searching = !searching; if (!searching) query = ""
                    }
                    Spacer(Modifier.width(8.dp))
                    CircleButton(Icons.Filled.MoreHoriz, "Space menu") { spaceMenu = true }
                }
                Spacer(Modifier.height(10.dp))
                TabRowPlain(listOf(if (isDiscord) "Channels" else "Chats", "Media", "Members"), tab, { tab = it })
            }
        }
        Hairline()
        when (tab) {
            1 -> SpaceMediaGrid(spaceId)
            2 -> MemberList(spaceId)
            else -> if (reordering) {
                ReorderChats(spaceId, layout.all, onDone = { reordering = false })
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
                    if (layout.invites.isNotEmpty()) {
                        sectionHeader("inv", "Invites")
                        roomItems(layout.invites, spaceId, plus, isDiscord)
                    }
                    if (layout.subspaces.isNotEmpty()) {
                        items(layout.subspaces, key = { "sub" + it.id }) { sub ->
                            SubspaceRow(sub) { nav.selectedSpace.value = sub.id }
                        }
                    }
                    if (layout.pinned.isNotEmpty()) {
                        sectionHeader("pin", "Pinned", pinIcon = true)
                        roomItems(layout.pinned, spaceId, plus, isDiscord, pinnedIds = layout.pinned.map { it.id }.toSet(), keyPrefix = "p")
                    }
                    val hasCategories = layout.blocks.any { it.categoryId != null }
                    layout.blocks.forEach { block ->
                        if (block.categoryId != null) {
                            val cat = SpaceCategory(block.categoryId, block.name ?: "", block.collapsed)
                            item(key = "cat" + block.categoryId) {
                                CategoryHeader(
                                    name = block.name ?: "", collapsed = block.collapsed, count = block.rooms.size,
                                    onToggle = { scope.launch { PlusEdit.setCategoryCollapsed(spaceId, block.categoryId, !block.collapsed) } },
                                    onMenu = { categoryMenu = cat },
                                )
                            }
                            if (!block.collapsed) roomItems(block.rooms, spaceId, plus, isDiscord, keyPrefix = "c")
                        } else {
                            if (hasCategories && block.rooms.isNotEmpty()) item(key = "uncat") { Spacer(Modifier.height(8.dp)) }
                            roomItems(block.rooms, spaceId, plus, isDiscord, keyPrefix = "u")
                        }
                    }
                    if (layout.muted.isNotEmpty()) {
                        item(key = "mutedhdr") {
                            CategoryHeader("Muted · ${layout.muted.size}", collapsed = !mutedOpen, count = -1, onToggle = { mutedOpen = !mutedOpen }, onMenu = null)
                        }
                        if (mutedOpen) roomItems(layout.muted, spaceId, plus, isDiscord, keyPrefix = "m")
                    }
                    if (layout.all.isEmpty() && layout.invites.isEmpty() && layout.subspaces.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (query.isNotBlank()) "Nothing matches \"$query\"." else "No chats in this space yet.", style = Sbt.body, color = c.textMuted)
                                if (query.isBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { nav.push(Screen.SpaceRooms(spaceId)) }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) {
                                        Text("Browse rooms in this space")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (spaceMenu) {
        val mode = plus.sortMode(spaceId)
        val anyUnmuted = layout.all.any { !it.isMuted }
        val isStart = Prefs.startSpace == spaceId
        fun sortAction(label: String, m: SortMode) = SheetAction(
            label, if (mode == m) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
        ) { scope.launch { PlusEdit.setSortMode(spaceId, m) } }
        val actions = mutableListOf(
            sortAction("Recent activity first", SortMode.RECENT),
            sortAction("Unread first", SortMode.UNREAD),
            sortAction("Custom order", SortMode.CUSTOM),
            SheetAction("Reorder chats", Icons.Outlined.SwapVert) { reordering = true },
        )
        if (plus.childOrder(spaceId).isNotEmpty()) {
            actions += SheetAction("Reset chat order", Icons.Outlined.RestartAlt) {
                scope.launch { PlusEdit.setChildOrder(spaceId, null); PlusEdit.setSortMode(spaceId, SortMode.RECENT) }
            }
        }
        actions += SheetAction("New category", Icons.Outlined.CreateNewFolder) { ui.newCategoryFor = spaceId to emptyList() }
        actions += SheetAction("Mark all as read", Icons.Outlined.MarkChatRead) {
            scope.launch { RoomActions.bulkMarkRead(layout.all.filter { it.isUnread }.map { it.id }) }
        }
        actions += if (anyUnmuted) SheetAction("Mute all chats", Icons.Outlined.NotificationsOff) {
            scope.launch { RoomActions.bulkMute(layout.all.filter { !it.isMuted }.map { it.id }, true) }
        } else SheetAction("Unmute all chats", Icons.Outlined.Notifications) {
            scope.launch { RoomActions.bulkMute(layout.all.map { it.id }, false) }
        }
        actions += SheetAction("Space color", Icons.Outlined.Palette) { ui.colorFor = spaceId }
        actions += SheetAction("Media", Icons.Outlined.PhotoLibrary) { tab = 1 }
        actions += SheetAction(if (isStart) "Don't open this space on start" else "Open this space on start", Icons.Outlined.Home) {
            Prefs.startSpace = if (isStart) null else spaceId
            nav.toast(if (isStart) "The app opens where you left off" else "The app opens on ${space.realName}")
        }
        actions += SheetAction("Browse rooms in this space", Icons.Outlined.Explore) { nav.push(Screen.SpaceRooms(spaceId)) }
        actions += SheetAction("Invite people", Icons.Outlined.PersonAdd) { nav.push(Screen.Members(spaceId)) }
        actions += SheetAction("Space details", Icons.Outlined.Info) { nav.push(Screen.RoomDetails(spaceId)) }
        actions += SheetAction("Leave space", Icons.AutoMirrored.Outlined.ExitToApp, danger = true) { leaveSpace = true }
        ActionSheet(title = space.realName, actions = actions, onDismiss = { spaceMenu = false })
    }

    categoryMenu?.let { cat ->
        val blocks = plus.blockOrder(spaceId)
        val idx = blocks.indexOf(cat.id)
        val actions = mutableListOf(SheetAction("Rename", Icons.Outlined.DriveFileRenameOutline) { renameCategory = cat })
        if (idx > 0) actions += SheetAction("Move up", Icons.Outlined.ArrowUpward) { scope.launch { PlusEdit.moveCategory(spaceId, cat.id, -1) } }
        if (idx < blocks.size - 1) actions += SheetAction("Move down", Icons.Outlined.ArrowDownward) { scope.launch { PlusEdit.moveCategory(spaceId, cat.id, 1) } }
        actions += SheetAction("Delete", Icons.Outlined.Delete, danger = true, subtitle = "Its chats stay in the space") { deleteCategory = cat }
        ActionSheet(title = cat.name, actions = actions, onDismiss = { categoryMenu = null })
    }
    renameCategory?.let { cat ->
        TextInputDialog("Rename category", initial = cat.name, onDismiss = { renameCategory = null }) { n ->
            scope.launch { PlusEdit.renameCategory(spaceId, cat.id, n.trim()) }
        }
    }
    deleteCategory?.let { cat ->
        ConfirmDialog("Delete \"${cat.name}\"?", "The chats in it move back to the chats without a category.", "Delete", danger = true,
            onDismiss = { deleteCategory = null }) { scope.launch { PlusEdit.deleteCategory(spaceId, cat.id) } }
    }
    if (leaveSpace) {
        ConfirmDialog("Leave ${space.realName}?", "You'll stay in its chats. Leave them separately if you want to.", "Leave", danger = true,
            onDismiss = { leaveSpace = false }) {
            nav.selectedSpace.value = null
            scope.launch { runCatching { RoomActions.leave(spaceId) }.onFailure { nav.toast("Couldn't leave: ${it.message}") } }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, text: String, pinIcon: Boolean = false) {
    item(key = "hdr$key") {
        val c = Sbt.c
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pinIcon) { Icon(Icons.Filled.PushPin, null, tint = c.textMuted, modifier = Modifier.size(13.dp).rotate(30f)); Spacer(Modifier.width(6.dp)) }
            Kicker(text)
        }
    }
}

private fun LazyListScope.roomItems(
    rooms: List<RoomSummary>,
    spaceId: String,
    plus: Plus,
    discordStyle: Boolean,
    pinnedIds: Set<String> = emptySet(),
    keyPrefix: String = "",
) {
    items(rooms, key = { keyPrefix + it.id }) { s -> SpaceRoomRow(s, spaceId, plus, discordStyle, s.id in pinnedIds || s.id in plus.pins(spaceId)) }
}

@Composable
private fun SpaceRoomRow(s: RoomSummary, spaceId: String, plus: Plus, discordStyle: Boolean, pinned: Boolean) {
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val scope = rememberCoroutineScope()
    SwipeActions(
        unread = s.isUnread,
        pinned = pinned,
        onToggleRead = { scope.launch { runCatching { if (s.isUnread) RoomActions.markRead(s.id) else RoomActions.markUnread(s.id) } } },
        onPin = { scope.launch { PlusEdit.setPinned(spaceId, s.id, !pinned) } },
        enabled = !ui.selecting && !s.isInvite,
    ) {
        val click = { if (ui.selecting) ui.toggle(s.id) else nav.openChat(s.id) }
        val long = { if (ui.selecting) ui.toggle(s.id) else ui.roomMenu = s to spaceId }
        if (discordStyle && HomeData.isChannel(s)) ChannelRow(s, plus, s.id in ui.selection, click, long)
        else ChatRow(s, plus, s.id in ui.selection, click, long)
    }
}

@Composable
private fun SubspaceRow(s: RoomSummary, onClick: () -> Unit) {
    val c = Sbt.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(s.avatarUrl, s.realName, size = 40.dp, key = s.id, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.realName, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Space", style = Sbt.small, color = c.textMuted)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = c.textFaint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, count: Int, onToggle: () -> Unit, onMenu: (() -> Unit)?) {
    val c = Sbt.c
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onMenu).padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(18.dp).rotate(if (collapsed) -90f else 0f))
        Spacer(Modifier.width(6.dp))
        Kicker(if (collapsed && count > 0) "$name · $count" else name, modifier = Modifier.weight(1f))
        if (onMenu != null) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreHoriz, "Category menu", tint = c.textFaint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Drag the ≡ handles to arrange the chats of a space. */
@Composable
private fun ReorderChats(spaceId: String, rooms: List<RoomSummary>, onDone: () -> Unit) {
    val c = Sbt.c
    val scope = rememberCoroutineScope()
    val plus by Sb.matrix.accountData.state.collectAsState()
    var list by remember { mutableStateOf(rooms) }
    LaunchedEffect(Unit) { list = rooms }
    val state = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(state) { from, to ->
        val f = list.indexOfFirst { it.id == from.key }; val t = list.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) list = list.toMutableList().apply { add(t, removeAt(f)) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.Sort, null, tint = c.textMuted)
            Spacer(Modifier.width(10.dp))
            Text("Drag the handles to arrange", style = Sbt.small, color = c.textMuted, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch { PlusEdit.setChildOrder(spaceId, list.map { it.id }); PlusEdit.setSortMode(spaceId, SortMode.CUSTOM) }
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Done") }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { s ->
                ReorderableItem(reorder, key = s.id) { dragging ->
                    Row(
                        Modifier.fillMaxWidth().background(if (dragging) c.raised else c.background).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(s.avatarUrl, plus.displayName(s), size = 38.dp, key = s.id)
                        Spacer(Modifier.width(14.dp))
                        Text(plus.displayName(s), fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.DragHandle, "Drag", tint = c.textMuted, modifier = Modifier.draggableHandle().padding(8.dp))
                    }
                }
            }
        }
    }
}
