package app.switchboard.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import app.switchboard.core.Sb
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt

val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥")

/**
 * Full-height emoji picker. It's a plain dialog rather than a bottom sheet: a sheet's
 * drag gesture fights the picker's own scrolling, so the list couldn't scroll.
 */
@Composable
fun EmojiPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Sbt.c
    val light = c.isLight
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pick an emoji", style = Sbt.body, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = c.textMuted) }
            }
            AndroidView(
                factory = { ctx ->
                    val themed = android.view.ContextThemeWrapper(ctx, if (light) app.switchboard.R.style.Theme_Switchboard_PickerLight else app.switchboard.R.style.Theme_Switchboard_Picker)
                    EmojiPickerView(themed).apply {
                        setOnEmojiPickedListener { item -> onPick(item.emoji); onDismiss() }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

data class MsgAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val danger: Boolean = false, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(ev: TEvent, actions: List<MsgAction>, onReact: (String) -> Unit, onMoreReactions: () -> Unit, onDismiss: () -> Unit) {
    val c = Sbt.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            if (ev.eventId != null && ev.body !is Body.Redacted) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickReactions.forEach { e ->
                        val mine = ev.reactions.any { it.key == e && it.mine }
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(if (mine) c.accent.copy(alpha = 0.3f) else c.raised)
                                .clickable { onDismiss(); onReact(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(c.raised).clickable { onDismiss(); onMoreReactions() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, "More reactions", tint = c.text) }
                }
            }
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); a.run() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(a.icon, null, tint = if (a.danger) c.danger else c.textMuted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(a.label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (a.danger) c.danger else c.text)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

fun shareText(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

/** Picks a chat to forward a message to. */
@Composable
fun ForwardDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Sbt.c
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val rooms = remember(summaries, order, query) {
        order.mapNotNull { summaries[it] }.filter { !it.isSpace && !it.isInvite && it.canSendMessages }
            .filter { query.isBlank() || plus.displayName(it).contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(rooms, key = { it.id }) { s ->
                        Row(Modifier.fillMaxWidth().clickable { onDismiss(); onPick(s.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(s.avatarUrl, plus.displayName(s), size = 36.dp, key = s.id)
                            Spacer(Modifier.width(12.dp))
                            Text(plus.displayName(s), color = c.text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SourceDialog(json: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event source") },
        text = {
            Box(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(json, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Sbt.c.text)
            }
        },
        confirmButton = { TextButton(onClick = { copyText(context, json); onDismiss() }) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

object ActionIcons {
    val reply = Icons.AutoMirrored.Filled.Reply
    val edit = Icons.Outlined.Edit
    val copy = Icons.Outlined.ContentCopy
    val forward = Icons.AutoMirrored.Outlined.Forward
    val pin = Icons.Outlined.PushPin
    val delete = Icons.Outlined.Delete
    val save = Icons.Outlined.Download
    val share = Icons.Outlined.Share
    val source = Icons.Outlined.Code
    val report = Icons.Outlined.Flag
    val link = Icons.Outlined.Link
}
