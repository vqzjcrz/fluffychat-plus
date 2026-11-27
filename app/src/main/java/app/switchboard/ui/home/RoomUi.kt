package app.switchboard.ui.home

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.switchboard.core.BulkProgress
import app.switchboard.core.PlusEdit
import app.switchboard.core.RoomActions
import app.switchboard.core.RoomSummary
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Nav
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.ProgressDialog
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

data class MoveRequest(val roomIds: List<String>, val fromSpace: String?)
data class CategoryRequest(val spaceId: String, val roomIds: List<String>)

/** Menus and dialogs for chats, shared by the home screen and chats. */
class RoomUi {
    var roomMenu by mutableStateOf<Pair<RoomSummary, String?>?>(null)
    var snoozeFor by mutableStateOf<List<String>?>(null)
    var renameFor by mutableStateOf<RoomSummary?>(null)
    var moveFor by mutableStateOf<MoveRequest?>(null)
    var categoryFor by mutableStateOf<CategoryRequest?>(null)
    var leaveFor by mutableStateOf<List<String>?>(null)
    var colorFor by mutableStateOf<String?>(null)
    var newCategoryFor by mutableStateOf<Pair<String, List<String>>?>(null)

    /** Multi-select in the chat list or a space. */
    val selection = mutableStateListOf<String>()
    var selecting by mutableStateOf(false)

    fun startSelection(first: String) { selection.clear(); selection.add(first); selecting = true }
    fun toggle(id: String) {
        if (id in selection) selection.remove(id) else selection.add(id)
        if (selection.isEmpty()) selecting = false
    }
    fun endSelection() { selection.clear(); selecting = false }
}

val LocalRoomUi = compositionLocalOf { RoomUi() }

fun tomorrowAt8(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, 8); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** The long-press menu for a chat. */
fun roomMenuActions(s: RoomSummary, spaceId: String?, ui: RoomUi, nav: Nav, scope: CoroutineScope): List<SheetAction> {
    val plus = Sb.matrix.accountData.plus
    val list = mutableListOf<SheetAction>()
    if (s.isInvite) {
        list += SheetAction("Accept invite", Icons.Outlined.CheckCircle) { scope.launch { runCatching { RoomActions.join(s.id) }.onFailure { nav.toast("Couldn't join: ${it.message}") } } }
        list += SheetAction("Decline", Icons.AutoMirrored.Outlined.ExitToApp, danger = true) { scope.launch { runCatching { RoomActions.leave(s.id) } } }
        return list
    }
    list += SheetAction("Select", Icons.Outlined.CheckCircle) { ui.startSelection(s.id) }
    list += if (s.isUnread) SheetAction("Mark as read", Icons.Outlined.MarkChatRead) { scope.launch { runCatching { RoomActions.markRead(s.id) } } }
    else SheetAction("Mark as unread", Icons.Outlined.MarkChatUnread) { scope.launch { runCatching { RoomActions.markUnread(s.id) } } }
    if (spaceId != null) {
        val pinned = s.id in plus.pins(spaceId)
        list += SheetAction(if (pinned) "Unpin" else "Pin", Icons.Outlined.PushPin) { scope.launch { PlusEdit.setPinned(spaceId, s.id, !pinned) } }
    }
    list += SheetAction(if (s.isMuted) "Unmute" else "Mute", if (s.isMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff) {
        scope.launch { runCatching { Sb.matrix.setMuted(s.id, !s.isMuted); Sb.matrix.refreshRooms(listOf(s.id)) }.onFailure { nav.toast("Couldn't change: ${it.message}") } }
    }
    val snoozed = plus.snoozedUntil(s.id) != null
    list += if (snoozed) SheetAction("End snooze", Icons.Outlined.Bedtime) { scope.launch { PlusEdit.setSnooze(s.id, null) } }
    else SheetAction("Snooze", Icons.Outlined.Bedtime) { ui.snoozeFor = listOf(s.id) }
    list += SheetAction("Rename for me", Icons.Outlined.DriveFileRenameOutline) { ui.renameFor = s }
    list += SheetAction("Move to space", Icons.AutoMirrored.Outlined.DriveFileMove) { ui.moveFor = MoveRequest(listOf(s.id), spaceId) }
    if (spaceId != null) {
        list += SheetAction("Move to category", Icons.AutoMirrored.Outlined.Label) { ui.categoryFor = CategoryRequest(spaceId, listOf(s.id)) }
        if (Sb.matrix.summary(spaceId)?.canEditSpace == true) {
            list += SheetAction("Remove from this space", Icons.Outlined.RemoveCircleOutline) {
                scope.launch { runCatching { RoomActions.removeFromSpace(s.id, spaceId) }.onFailure { nav.toast("Couldn't remove: ${it.message}") } }
            }
        }
    }
    if (s.canSendMessages) {
        val ro = HomeData.isReadOnly(s, plus)
        list += SheetAction(if (ro) "Allow posting" else "Make read-only", if (ro) Icons.Outlined.LockOpen else Icons.Outlined.Lock) {
            scope.launch { RoomActions.setReadOnly(s, !ro) }
        }
    }
    list += SheetAction("Leave", Icons.AutoMirrored.Outlined.ExitToApp, danger = true) { ui.leaveFor = listOf(s.id) }
    return list
}

/** Shows whichever chat menu or dialog is open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomUiHost(ui: RoomUi) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val c = Sbt.c
    val summaries by Sb.matrix.summaries.collectAsState()
    val progress by RoomActions.progress.collectAsState()

    ui.roomMenu?.let { (s, space) ->
        val plus = Sb.matrix.accountData.plus
        ActionSheet(
            title = null,
            actions = roomMenuActions(s, space, ui, nav, scope),
            onDismiss = { ui.roomMenu = null },
            header = {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.avatarUrl, plus.displayName(s), size = 40.dp, key = s.id)
                    Spacer(Modifier.width(14.dp))
                    Text(plus.displayName(s), fontFamily = Inter, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2)
                }
            },
        )
    }

    ui.snoozeFor?.let { ids ->
        ActionSheet(
            title = "Snooze notifications",
            actions = listOf(
                SheetAction("For 1 hour") { scope.launch { ids.forEach { PlusEdit.setSnooze(it, System.currentTimeMillis() + 3_600_000) } } },
                SheetAction("For 8 hours") { scope.launch { ids.forEach { PlusEdit.setSnooze(it, System.currentTimeMillis() + 8 * 3_600_000) } } },
                SheetAction("Until 8:00 tomorrow") { scope.launch { ids.forEach { PlusEdit.setSnooze(it, tomorrowAt8()) } } },
            ),
            onDismiss = { ui.snoozeFor = null },
        )
    }

    ui.renameFor?.let { s ->
        val current = Sb.matrix.accountData.plus.nickname(s.id) ?: ""
        TextInputDialog(
            title = "Rename for me",
            initial = current,
            hint = s.realName,
            message = "Only you see this name. Leave it empty to use the chat's own name.",
            allowEmpty = true,
            onDismiss = { ui.renameFor = null },
            onConfirm = { name -> scope.launch { PlusEdit.setNickname(s.id, name) } },
        )
    }

    ui.leaveFor?.let { ids ->
        val name = if (ids.size == 1) summaries[ids[0]]?.let { Sb.matrix.accountData.plus.displayName(it) } ?: "this chat" else "${ids.size} chats"
        ConfirmDialog(
            title = "Leave $name?",
            message = if (ids.size == 1) null else "Invites among them are declined.",
            confirm = "Leave", danger = true,
            onDismiss = { ui.leaveFor = null },
            onConfirm = {
                ui.endSelection()
                scope.launch {
                    val failed = if (ids.size == 1) { runCatching { RoomActions.leave(ids[0]) }.fold({ 0 }, { 1 }) } else RoomActions.bulkLeave(ids)
                    if (failed > 0) nav.toast("Couldn't leave $failed chat${if (failed > 1) "s" else ""}")
                }
            },
        )
    }

    ui.moveFor?.let { req ->
        val spaces = summaries.values.filter { it.isSpace && !it.isInvite && it.canEditSpace && it.id != req.fromSpace }
            .sortedBy { it.realName.lowercase() }
        AlertDialog(
            onDismissRequest = { ui.moveFor = null },
            title = { Text(if (req.roomIds.size == 1) "Move to space" else "Move ${req.roomIds.size} chats to space") },
            text = {
                if (spaces.isEmpty()) Text("You don't have any spaces you can add chats to. Create one with + in the spaces bar.")
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(spaces, key = { it.id }) { sp ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                ui.moveFor = null
                                ui.endSelection()
                                scope.launch {
                                    runCatching { RoomActions.moveToSpace(req.roomIds, sp.id, req.fromSpace) }
                                        .onSuccess { nav.toast("Moved to ${sp.realName}") }
                                        .onFailure { nav.toast("Couldn't move: ${it.message}") }
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(sp.avatarUrl, sp.realName, size = 36.dp, key = sp.id)
                            Spacer(Modifier.width(12.dp))
                            Text(sp.realName, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { ui.moveFor = null }) { Text("Cancel") } },
        )
    }

    ui.categoryFor?.let { req ->
        val plus = Sb.matrix.accountData.plus
        val cats = plus.categories(req.spaceId)
        val actions = cats.map { cat ->
            SheetAction(cat.name, Icons.AutoMirrored.Outlined.Label) {
                ui.endSelection()
                scope.launch { PlusEdit.setCategoryOf(req.spaceId, req.roomIds, cat.id) }
            }
        } + listOf(
            SheetAction("No category", Icons.Outlined.RemoveCircleOutline) {
                ui.endSelection()
                scope.launch { PlusEdit.setCategoryOf(req.spaceId, req.roomIds, null) }
            },
            SheetAction("New category…", Icons.Filled.Add) { ui.newCategoryFor = req.spaceId to req.roomIds },
        )
        ActionSheet(title = "Move to category", actions = actions, onDismiss = { ui.categoryFor = null })
    }

    ui.newCategoryFor?.let { (spaceId, ids) ->
        TextInputDialog(
            title = "New category", hint = "Name", confirm = "Create",
            onDismiss = { ui.newCategoryFor = null },
            onConfirm = { name ->
                ui.endSelection()
                scope.launch {
                    val id = PlusEdit.addCategory(spaceId, name.trim())
                    if (ids.isNotEmpty()) PlusEdit.setCategoryOf(spaceId, ids, id)
                }
            },
        )
    }

    ui.colorFor?.let { spaceId ->
        val current = Sb.matrix.accountData.plus.spaceColor(spaceId)
        AlertDialog(
            onDismissRequest = { ui.colorFor = null },
            title = { Text("Space color") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    spaceColors.forEach { col ->
                        val argb = col.toArgb()
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(col)
                                .then(if (current == argb) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                                .clickable { ui.colorFor = null; scope.launch { PlusEdit.setSpaceColor(spaceId, argb) } },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ui.colorFor = null; scope.launch { PlusEdit.setSpaceColor(spaceId, null) } }) { Text("No color") } },
            dismissButton = { TextButton(onClick = { ui.colorFor = null }) { Text("Cancel") } },
        )
    }

    progress?.let { p: BulkProgress -> ProgressDialog(p.title, p.done, p.total, p.note) }
}

val spaceColors = listOf(
    Color(0xFFE5484D), Color(0xFFF76B15), Color(0xFFFFC53D), Color(0xFFD4FF3F), Color(0xFF46A758),
    Color(0xFF12A594), Color(0xFF00A2C7), Color(0xFF0090FF), Color(0xFF3E63DD), Color(0xFF6E56CF),
    Color(0xFF8E4EC6), Color(0xFFD6409F), Color(0xFFE93D82), Color(0xFFAD7F58), Color(0xFF8B8D98),
)
