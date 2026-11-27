package app.switchboard.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Bridges
import app.switchboard.core.PlusEdit
import app.switchboard.core.Prefs
import app.switchboard.core.RoomActions
import app.switchboard.core.Sb
import app.switchboard.core.ScheduledMessages
import app.switchboard.core.Sending
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.CircleButton
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.home.LocalRoomUi
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import app.switchboard.ui.theme.SwitchboardTheme
import app.switchboard.ui.theme.currentColors
import app.switchboard.ui.theme.withAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.EditedContent
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.RoomMember
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TypingNotificationsListener
import org.matrix.rustcomponents.sdk.contentWithoutRelationFromMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(roomId: String, eventId: String?) {
    val nav = LocalNav.current
    val summaries by Sb.matrix.summaries.collectAsState()
    val children by Sb.matrix.spaceChildren.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    val room = remember(roomId, summaries.containsKey(roomId)) { Sb.matrix.room(roomId) }
    val summary = summaries[roomId]
    if (room == null || summary == null) {
        Column(Modifier.fillMaxSize().background(Sbt.c.background)) {
            app.switchboard.ui.common.ScreenHeader("Chat", onBack = { nav.pop() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!Sb.matrix.loaded.collectAsState().value) CircularProgressIndicator(color = Sbt.c.accent)
                else Text("This chat isn't available.", color = Sbt.c.textMuted)
            }
        }
        return
    }
    // The chat takes the color of the space it's in, if that space has one.
    val spaceForColor = remember(roomId, children, plus, nav.selectedSpace.value) {
        val sel = nav.selectedSpace.value
        if (sel != null && roomId in HomeData.descendants(sel, children) && plus.spaceColor(sel) != null) sel
        else children.entries.firstOrNull { (k, v) -> v.any { it.roomId == roomId } && plus.spaceColor(k) != null }?.key
    }
    val colors = currentColors().withAccent(spaceForColor?.let { plus.spaceColor(it) }?.let { Color(it) })
    SwitchboardTheme(colors = colors) {
        if (summary.isInvite) InviteView(roomId) else ChatContent(roomId, room, eventId)
    }
}

@Composable
private fun InviteView(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val s = Sb.matrix.summaries.collectAsState().value[roomId] ?: return
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        app.switchboard.ui.common.ScreenHeader(s.realName, onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar(s.avatarUrl, s.realName, size = 96.dp, key = s.id)
            Spacer(Modifier.height(18.dp))
            Text(s.realName, style = Sbt.title.copy(fontSize = 26.sp), color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("${s.inviterName ?: "Someone"} invited you", style = Sbt.body, color = c.textMuted)
            Spacer(Modifier.height(28.dp))
            Button(
                enabled = !busy,
                onClick = { busy = true; scope.launch { runCatching { RoomActions.join(roomId) }.onFailure { nav.toast("Couldn't join: ${it.message}") }; busy = false } },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Accept") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = { busy = true; scope.launch { runCatching { RoomActions.leave(roomId) }; nav.pop() } },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Decline", color = c.danger) }
        }
    }
}

@Composable
private fun ChatContent(roomId: String, room: org.matrix.rustcomponents.sdk.Room, focusEventId: String?) {
    val c = Sbt.c
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val summary = Sb.matrix.summaries.collectAsState().value[roomId] ?: return
    val plus by Sb.matrix.accountData.state.collectAsState()
    val bridges by Bridges.info.collectAsState()
    val scheduledAll by ScheduledMessages.all.collectAsState()
    val swipeLeftToRight by Prefs.swipeToReplyLeftToRight.collectAsState()
    val hideState by Prefs.hideStateEvents.collectAsState()

    var focus by remember(roomId) { mutableStateOf<String?>(null) }
    val tl = remember(roomId, focus) { TimelineState(room, focus) }
    var info by remember(roomId) { mutableStateOf<org.matrix.rustcomponents.sdk.RoomInfo?>(null) }
    LaunchedEffect(roomId, summary) { info = runCatching { room.roomInfo() }.getOrNull() }
    val composer = remember(roomId) { ComposerState() }
    var members by remember(roomId) { mutableStateOf<List<RoomMember>>(emptyList()) }
    var typing by remember(roomId) { mutableStateOf<List<String>>(emptyList()) }
    DisposableEffect(tl) {
        tl.start()
        onDispose { tl.close() }
    }
    DisposableEffect(roomId) {
        Sb.matrix.subscribeToRoom(roomId)
        app.switchboard.push.Notifier.activeRoom = roomId
        app.switchboard.push.Notifier.cancel(context, roomId)
        var h: TaskHandle? = null
        try {
            h = room.subscribeToTypingNotifications(object : TypingNotificationsListener {
                override fun call(typingUserIds: List<String>) { typing = typingUserIds.filter { it != Sb.matrix.userId } }
            })
        } catch (_: Exception) {}
        onDispose {
            if (app.switchboard.push.Notifier.activeRoom == roomId) app.switchboard.push.Notifier.activeRoom = null
            h?.cancel()
            scope.launch { runCatching { room.typingNotice(false) } }
        }
    }
    LaunchedEffect(roomId) {
        members = try {
            val it = room.membersNoSync()
            val out = ArrayList<RoomMember>()
            while (true) { val chunk = it.nextChunk(500u) ?: break; out.addAll(chunk); if (chunk.isEmpty()) break }
            out.filter { m -> m.membership is org.matrix.rustcomponents.sdk.MembershipState.Join }
        } catch (_: Exception) { emptyList() }
    }

    val allItems by tl.items.collectAsState()
    val paginating by tl.paginating.collectAsState()
    val items = remember(allItems, hideState) {
        if (!hideState) allItems else allItems.filter { it !is TEvent || it.isMessage }
    }
    val reversed = remember(items) { items.asReversed() }
    val listState = rememberLazyListState()

    // Mark as read when new messages show up.
    val lastKey = items.lastOrNull { it is TEvent }?.key
    LaunchedEffect(lastKey, focus) {
        if (lastKey != null && focus == null) {
            delay(400)
            runCatching { tl.timeline?.markAsRead(ReceiptType.READ) }
            if (summary.markedUnread) runCatching { room.setUnreadFlag(false) }
        }
    }
    // Load older messages near the top.
    LaunchedEffect(listState, reversed.size, tl) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= reversed.size - 6) tl.paginate() }
    }
    LaunchedEffect(listState, tl) {
        if (tl.focus != null) snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { first -> if (first <= 3) tl.paginateForward() }
    }

    // Follow new messages: always for your own, and for others' when you're already at the bottom.
    var sentAt by remember { mutableLongStateOf(0L) }
    val newest = reversed.firstOrNull { it is TEvent } as? TEvent
    LaunchedEffect(newest?.key) {
        if (newest == null || tl.focus != null) return@LaunchedEffect
        withFrameNanos { }
        val justSent = System.currentTimeMillis() - sentAt < 10_000
        if (newest.isOwn && justSent) listState.scrollToItem(0)
        else if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    var highlight by remember { mutableStateOf<String?>(null) }
    fun indexOf(eventId: String): Int {
        val list = if (hideState) tl.items.value.filter { it !is TEvent || it.isMessage } else tl.items.value
        return list.asReversed().indexOfFirst { (it as? TEvent)?.eventId == eventId }
    }
    fun jumpTo(eventId: String) {
        scope.launch {
            repeat(3) {
                val i = indexOf(eventId)
                if (i >= 0) {
                    listState.animateScrollToItem(i)
                    highlight = eventId
                    delay(1800); highlight = null
                    return@launch
                }
                tl.paginate()
                delay(700)
            }
            // Too far back for the live timeline: open the chat around that message.
            focus = eventId
        }
    }
    LaunchedEffect(tl, items.size) {
        val f = tl.focus ?: return@LaunchedEffect
        val i = indexOf(f)
        if (i >= 0 && highlight == null) {
            listState.scrollToItem(i)
            highlight = f
            delay(1800); highlight = null
        }
    }
    LaunchedEffect(focusEventId, items.isNotEmpty()) { if (focusEventId != null && items.isNotEmpty()) jumpTo(focusEventId) }

    var actionFor by remember { mutableStateOf<TEvent?>(null) }
    var reactionPickerFor by remember { mutableStateOf<TEvent?>(null) }
    var forwardFor by remember { mutableStateOf<TEvent?>(null) }
    var sourceFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<TEvent?>(null) }
    var viewerKey by remember { mutableStateOf<String?>(null) }
    var chatMenu by remember { mutableStateOf(false) }
    var sendLater by remember { mutableStateOf(false) }
    var customLater by remember { mutableStateOf(false) }
    var scheduledSheet by remember { mutableStateOf(false) }
    var pinsSheet by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var reportFor by remember { mutableStateOf<TEvent?>(null) }

    val name = plus.displayName(summary)
    val isChannel = HomeData.isChannel(summary)
    val bridgeName = bridges[roomId]?.name ?: Bridges.guess(summary.heroIds)
    val readOnly = HomeData.isReadOnly(summary, plus)
    val kicker = listOfNotNull(
        bridgeName ?: if (summary.isDirect) "Direct message" else null,
        if (!summary.isDirect || summary.joinedCount > 2) "${summary.joinedCount} members" else null,
    ).joinToString(" · ").ifEmpty { "Chat" }
    val hint = if (isChannel) "Message #${name.removePrefix("#")}" else "Message $name"

    fun send() {
        val (md, ids) = composer.markdown()
        if (md.isBlank()) return
        val editing = composer.editing
        val reply = composer.replyTo
        composer.clear()
        if (focus != null) focus = null
        val focused = tl.focus != null
        scope.launch {
            runCatching { room.typingNotice(false) }
            val t = (if (focused) runCatching { room.timeline() }.getOrNull() else tl.timeline) ?: return@launch
            try {
                val content = Sending.content(md, ids)
                when {
                    editing != null -> t.edit(editing.id, EditedContent.RoomMessage(content))
                    reply?.eventId != null -> t.sendReply(content, reply.eventId)
                    else -> t.send(content)
                }
            } catch (e: Exception) { nav.toast("Couldn't send: ${e.message}") }
        }
        sentAt = System.currentTimeMillis()
        scope.launch { listState.scrollToItem(0) }
    }

    var lastTyping by remember { mutableLongStateOf(0L) }
    fun onTyping(active: Boolean) {
        val now = System.currentTimeMillis()
        if (active && now - lastTyping < 4000) return
        lastTyping = if (active) now else 0
        scope.launch { runCatching { room.typingNotice(active) } }
    }

    val viewerItems = remember(items) { items.mapNotNull { (it as? TEvent)?.toViewerItem(roomId) } }

    Box(
        Modifier.fillMaxSize().background(c.background).pointerInput(swipeLeftToRight) {
            if (!swipeLeftToRight) {
                var total = 0f
                detectHorizontalDragGestures(onDragEnd = { if (total > with(density) { 90.dp.toPx() }) panel = true; total = 0f }) { _, amount -> total += amount }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).clickable { nav.pop() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text)
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { nav.push(Screen.RoomDetails(roomId)) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(summary.avatarUrl, name, size = 42.dp, key = roomId)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Kicker(kicker)
                    }
                }
                CircleButton(Icons.Filled.MoreHoriz, "Chat menu") { chatMenu = true }
            }
            Hairline()
            PinsBar(roomId, summary.let { it.id }, items, plus.personalPins(roomId), onJump = { jumpTo(it) }, onOpenList = { pinsSheet = true })
            if (focus != null) {
                Row(
                    Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.14f)).clickable { focus = null }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Viewing older messages", style = Sbt.small, color = c.text, modifier = Modifier.weight(1f))
                    Text("Jump to latest", style = Sbt.small, color = c.accent, fontWeight = FontWeight.Bold)
                }
            }
            val scheduled = ScheduledMessages.forRoom(roomId, scheduledAll)
            if (scheduled.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(c.surface).clickable { scheduledSheet = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Schedule, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (scheduled.size == 1) "Scheduled for ${whenText(scheduled[0].sendAt)}" else "${scheduled.size} scheduled messages",
                        style = Sbt.small, color = c.text, modifier = Modifier.weight(1f),
                    )
                    Text("Manage", style = Sbt.small, color = c.accent, fontWeight = FontWeight.Bold)
                }
                Hairline()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val cb = remember(roomId) {
                    MessageCallbacks(
                        onLongPress = { actionFor = it },
                        onReply = { composer.replyTo = it; composer.editing = null },
                        onOpenMedia = { viewerKey = it.key },
                        onReact = { ev, key -> scope.launch { runCatching { tl.timeline?.toggleReaction(ev.id, key) } } },
                        onJumpTo = { jumpTo(it) },
                        onLink = { url -> handleLink(context, url, nav) },
                        onSender = { nav.push(Screen.UserProfile(it, roomId)) },
                        onRetry = { ev -> scope.launch { runCatching { ev.item.lazyProvider.getSendHandle()?.tryResend() } } },
                        onVote = { ev, answer -> ev.eventId?.let { id -> scope.launch { runCatching { tl.timeline?.sendPollResponse(id, listOf(answer)) } } } },
                        onOpenPanel = { panel = true },
                    )
                }
                LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(reversed, key = { _, it -> it.key }) { i, item ->
                        when (item) {
                            is TEvent -> {
                                val prev = (reversed.getOrNull(i + 1) as? TEvent)
                                val next = (reversed.getOrNull(i - 1) as? TEvent)
                                MessageItem(item, prev, next, summary.isDirect && summary.joinedCount <= 2, highlight != null && item.eventId == highlight, cb)
                            }
                            is TDate -> DateChip(item.ts)
                            is TReadMarker -> if (i > 0) NewMessagesLine()
                            is TStart -> Text(
                                "This is the start of ${name}.", style = Sbt.small, color = c.textFaint,
                                modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (paginating) item(key = "__loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp) }
                    }
                }
                val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 6 } }
                if (showDown) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = c.raised, contentColor = c.text,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                    ) { Icon(Icons.Filled.KeyboardArrowDown, "Jump to latest") }
                }
            }
            if (typing.isNotEmpty()) {
                val names = typing.map { id -> members.firstOrNull { it.userId == id }?.displayName ?: id.removePrefix("@").substringBefore(':') }
                Text(
                    when (names.size) { 1 -> "${names[0]} is typing…"; 2 -> "${names[0]} and ${names[1]} are typing…"; else -> "Several people are typing…" },
                    style = Sbt.tiny, color = c.accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            if (readOnly) {
                ReadOnlyNote(canPost = summary.canSendMessages) { scope.launch { RoomActions.setReadOnly(summary, false) } }
            } else {
                Composer(
                    state = composer, hint = hint, members = members,
                    onSend = { send() },
                    onSendLater = { sendLater = true },
                    onTyping = { onTyping(it) },
                    onPickedFiles = { uris ->
                        val reply = composer.replyTo?.eventId
                        composer.replyTo = null
                        scope.launch {
                            val t = tl.timeline ?: return@launch
                            for (u in uris) {
                                try {
                                    val lf = Sending.copyToCache(context, u)
                                    Sending.sendFile(context, t, lf, reply)
                                } catch (e: Exception) { nav.toast("Couldn't send the file: ${e.message}") }
                            }
                        }
                    },
                    onVoice = { file, ms, wf, mime ->
                        val reply = composer.replyTo?.eventId
                        composer.replyTo = null
                        scope.launch {
                            val t = tl.timeline ?: return@launch
                            runCatching { Sending.sendVoice(t, file, ms, wf, mime, reply) }.onFailure { nav.toast("Couldn't send the voice message: ${it.message}") }
                        }
                    },
                )
            }
        }

        // Spaces panel
        AnimatedVisibility(panel, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { panel = false })
        }
        AnimatedVisibility(panel, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            SpacesPanel(
                currentRoom = roomId,
                startSpace = nav.selectedSpace.value,
                onOpen = { id -> panel = false; if (id != roomId) nav.openChat(id) },
                onClose = { panel = false },
            )
        }
        BackHandler(enabled = panel) { panel = false }

        viewerKey?.let { key ->
            if (viewerItems.isNotEmpty()) MediaViewer(viewerItems, key) { viewerKey = null }
        }
    }

    // --- menus and dialogs ---
    actionFor?.let { ev ->
        val actions = mutableListOf<MsgAction>()
        if (ev.canReply && !readOnly) actions += MsgAction("Reply", ActionIcons.reply) { composer.replyTo = ev; composer.editing = null }
        if (ev.isOwn && ev.canEdit && ev.body is Body.Text) actions += MsgAction("Edit", ActionIcons.edit) { composer.startEdit(ev) }
        if (ev.body is Body.Text) actions += MsgAction("Copy", ActionIcons.copy) { copyText(context, ev.plainText); nav.toast("Copied") }
        if (ev.eventId != null && ev.body !is Body.Redacted && ev.body !is Body.Encrypted) actions += MsgAction("Forward", ActionIcons.forward) { forwardFor = ev }
        ev.eventId?.let { id ->
            val personal = id in plus.personalPins(roomId)
            actions += MsgAction(if (personal) "Unpin for me" else "Pin for me", ActionIcons.pin) {
                scope.launch { PlusEdit.setPersonalPin(roomId, id, !personal) }
            }
            if (summary.canPin) {
                val roomPinned = info?.pinnedEventIds?.contains(id) == true
                actions += MsgAction(if (roomPinned) "Unpin for everyone" else "Pin for everyone", ActionIcons.pin) {
                    scope.launch { runCatching { if (roomPinned) tl.timeline?.unpinEvent(id) else tl.timeline?.pinEvent(id) }.onFailure { nav.toast("Couldn't pin: ${it.message}") } }
                }
            }
        }
        ev.toViewerItem(roomId)?.let { vi ->
            actions += MsgAction("Save to phone", ActionIcons.save) { scope.launch { saveMedia(context, vi) } }
        }
        if (ev.body is Body.File) {
            val b = ev.body as Body.File
            actions += MsgAction("Open", ActionIcons.share) { scope.launch { openAttachment(context, b.source, b.filename, b.mime ?: "application/octet-stream") } }
        }
        if (ev.eventId != null) actions += MsgAction("Copy link", ActionIcons.link) {
            scope.launch { runCatching { copyText(context, room.matrixToEventPermalink(ev.eventId)); nav.toast("Link copied") } }
        }
        if (ev.body !is Body.Redacted && (ev.isOwn || runCatching { info?.powerLevels?.canOwnUserRedactOther() }.getOrNull() == true)) {
            actions += MsgAction("Delete", ActionIcons.delete, danger = true) { deleteFor = ev }
        }
        actions += MsgAction("View source", ActionIcons.source) {
            sourceFor = runCatching { ev.item.lazyProvider.debugInfo().originalJson }.getOrNull()?.let { prettyJson(it) } ?: "Not available"
        }
        if (!ev.isOwn && ev.eventId != null) actions += MsgAction("Report", ActionIcons.report, danger = true) { reportFor = ev }
        MessageActionSheet(
            ev, actions,
            onReact = { key -> scope.launch { runCatching { tl.timeline?.toggleReaction(ev.id, key) } } },
            onMoreReactions = { reactionPickerFor = ev },
            onDismiss = { actionFor = null },
        )
    }
    reactionPickerFor?.let { ev ->
        EmojiPickerSheet(onDismiss = { reactionPickerFor = null }) { emoji ->
            scope.launch { runCatching { tl.timeline?.toggleReaction(ev.id, emoji) } }
        }
    }
    forwardFor?.let { ev ->
        ForwardDialog(onDismiss = { forwardFor = null }) { target ->
            scope.launch {
                try {
                    val c2 = ev.item.content as? TimelineItemContent.MsgLike
                    val msg = (c2?.content?.kind as? MsgLikeKind.Message)?.content ?: throw IllegalStateException("This message can't be forwarded")
                    val r = Sb.matrix.room(target) ?: throw IllegalStateException("Chat not found")
                    r.timeline().send(contentWithoutRelationFromMessage(msg))
                    nav.toast("Forwarded")
                } catch (e: Exception) { nav.toast("Couldn't forward: ${e.message}") }
            }
        }
    }
    sourceFor?.let { SourceDialog(it) { sourceFor = null } }
    deleteFor?.let { ev ->
        ConfirmDialog("Delete this message?", "It's removed for everyone.", "Delete", danger = true, onDismiss = { deleteFor = null }) {
            scope.launch { runCatching { tl.timeline?.redactEvent(ev.id, null) }.onFailure { nav.toast("Couldn't delete: ${it.message}") } }
        }
    }
    reportFor?.let { ev ->
        TextInputDialog("Report message", hint = "Reason (optional)", confirm = "Report", allowEmpty = true, onDismiss = { reportFor = null }) { reason ->
            scope.launch { runCatching { room.reportContent(ev.eventId!!, reason.ifBlank { null }) }.onSuccess { nav.toast("Reported") }.onFailure { nav.toast("Couldn't report: ${it.message}") } }
        }
    }
    if (chatMenu) {
        val snoozed = plus.snoozedUntil(roomId) != null
        val actions = mutableListOf(
            SheetAction("Chat details", Icons.Outlined.Info) { nav.push(Screen.RoomDetails(roomId)) },
            SheetAction("Search messages", Icons.Outlined.Search) { nav.push(Screen.Search(roomId)) },
            SheetAction(if (summary.isMuted) "Unmute" else "Mute", if (summary.isMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff) {
                scope.launch { runCatching { Sb.matrix.setMuted(roomId, !summary.isMuted); Sb.matrix.refreshRooms(listOf(roomId)) } }
            },
            if (snoozed) SheetAction("End snooze", Icons.Outlined.Bedtime) { scope.launch { PlusEdit.setSnooze(roomId, null) } }
            else SheetAction("Snooze", Icons.Outlined.Bedtime) { ui.snoozeFor = listOf(roomId) },
            SheetAction("Rename for me", Icons.Outlined.DriveFileRenameOutline) { ui.renameFor = summary },
            SheetAction("Mark as unread", Icons.Outlined.MarkChatUnread) { scope.launch { runCatching { RoomActions.markUnread(roomId) }; nav.pop() } },
        )
        if (summary.canSendMessages) {
            actions += SheetAction(if (readOnly) "Allow posting" else "Make read-only", if (readOnly) Icons.Outlined.LockOpen else Icons.Outlined.Lock) {
                scope.launch { RoomActions.setReadOnly(summary, !readOnly) }
            }
        }
        actions += SheetAction("Invite people", Icons.Outlined.PersonAdd) { nav.push(Screen.Members(roomId)) }
        actions += SheetAction("Leave", Icons.AutoMirrored.Outlined.ExitToApp, danger = true) { ui.leaveFor = listOf(roomId) }
        ActionSheet(title = name, actions = actions, onDismiss = { chatMenu = false })
    }
    if (sendLater) {
        ActionSheet(
            title = "Send later",
            actions = listOf(
                SheetAction("In 1 hour") { scheduleMessage(room, composer, System.currentTimeMillis() + 3_600_000, scope, nav) },
                SheetAction("In 3 hours") { scheduleMessage(room, composer, System.currentTimeMillis() + 3 * 3_600_000, scope, nav) },
                SheetAction("Tonight at 20:00") { scheduleMessage(room, composer, todayAt(20), scope, nav) },
                SheetAction("Tomorrow at 8:00") { scheduleMessage(room, composer, app.switchboard.ui.home.tomorrowAt8(), scope, nav) },
                SheetAction("Pick date and time…") { customLater = true },
            ),
            onDismiss = { sendLater = false },
        )
    }
    if (customLater) {
        DateTimePickerDialog(onDismiss = { customLater = false }) { ts -> scheduleMessage(room, composer, ts, scope, nav) }
    }
    if (scheduledSheet) {
        val list = ScheduledMessages.forRoom(roomId, scheduledAll)
        ActionSheet(
            title = "Scheduled messages",
            actions = list.flatMap { s ->
                listOf(
                    SheetAction("Send now: ${s.body.take(40)}", Icons.Filled.Schedule, subtitle = whenText(s.sendAt)) {
                        scope.launch { runCatching { ScheduledMessages.sendNow(s) }.onFailure { nav.toast("Couldn't send: ${it.message}") } }
                    },
                    SheetAction("Cancel: ${s.body.take(40)}", Icons.Outlined.Info, danger = true) { scope.launch { ScheduledMessages.cancel(s) } },
                )
            },
            onDismiss = { scheduledSheet = false },
        )
    }
    if (pinsSheet) {
        PinsSheet(roomId, room, items, onJump = { jumpTo(it) }, onDismiss = { pinsSheet = false })
    }
}

private fun scheduleMessage(room: org.matrix.rustcomponents.sdk.Room, composer: ComposerState, at: Long, scope: kotlinx.coroutines.CoroutineScope, nav: app.switchboard.ui.Nav) {
    val text = composer.text.text.trim()
    if (text.isEmpty()) return
    scope.launch {
        try {
            if (!ScheduledMessages.supported()) {
                nav.toast("Your homeserver doesn't have delayed events turned on (max_event_delay_duration in Synapse).")
                return@launch
            }
            ScheduledMessages.schedule(room, text, at)
            composer.clear()
            nav.toast("Scheduled for ${whenText(at)}")
        } catch (e: Exception) { nav.toast("Couldn't schedule: ${e.message}") }
    }
}

fun todayAt(hour: Int): Long {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.HOUR_OF_DAY) >= hour) cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun whenText(ts: Long): String = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(Date(ts))

private fun prettyJson(s: String) = runCatching { org.json.JSONObject(s).toString(2) }.getOrDefault(s)

@Composable
private fun DateChip(ts: Long) {
    val c = Sbt.c
    val label = remember(ts) {
        val now = Calendar.getInstance(); val d = Calendar.getInstance().apply { timeInMillis = ts }
        when {
            now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR) -> "Today"
            now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - d.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
            now.get(Calendar.YEAR) == d.get(Calendar.YEAR) -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(ts))
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Kicker(label, color = c.textFaint)
    }
}

@Composable
private fun NewMessagesLine() {
    val c = Sbt.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(c.danger.copy(alpha = 0.6f)))
        Kicker("New", color = c.danger, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(c.danger.copy(alpha = 0.6f)))
    }
}

/** Bar under the title with the latest pinned message (for everyone, or just for you). */
@Composable
private fun PinsBar(roomId: String, @Suppress("UNUSED_PARAMETER") id: String, items: List<TItem>, personal: List<String>, onJump: (String) -> Unit, onOpenList: () -> Unit) {
    val c = Sbt.c
    val room = remember(roomId) { Sb.matrix.room(roomId) }
    var roomPins by remember(roomId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(roomId, items.size) { roomPins = runCatching { room?.roomInfo()?.pinnedEventIds ?: emptyList() }.getOrDefault(emptyList()) }
    val all = (roomPins + personal).distinct()
    if (all.isEmpty()) return
    var index by remember(roomId) { mutableStateOf(0) }
    val current = all[index.coerceIn(0, all.size - 1)]
    var text by remember(current) { mutableStateOf<String?>(null) }
    LaunchedEffect(current, items) {
        text = (items.firstOrNull { (it as? TEvent)?.eventId == current } as? TEvent)?.let { "${it.senderName}: ${it.plainText}" }
            ?: runCatching {
                val ev = room?.loadOrFetchEvent(current)
                val content = ev?.content()
                if (content is org.matrix.rustcomponents.sdk.TimelineEventContent.MessageLike) app.switchboard.core.Previews.notificationBody(content.content) else null
            }.getOrNull() ?: "Pinned message"
    }
    Row(
        Modifier.fillMaxWidth().background(c.surface).clickable {
            onJump(current)
            if (all.size > 1) index = (index + 1) % all.size
        }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PushPin, null, tint = c.accent, modifier = Modifier.size(16.dp).rotate(30f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Kicker(
                (if (current in personal && current !in roomPins) "Pinned for you" else "Pinned") + if (all.size > 1) " · ${index + 1}/${all.size}" else "",
                color = c.accent,
            )
            Text(text ?: "…", style = Sbt.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onOpenList), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreHoriz, "All pins", tint = c.textMuted)
        }
    }
    Hairline()
}

@Composable
private fun PinsSheet(roomId: String, room: org.matrix.rustcomponents.sdk.Room, items: List<TItem>, onJump: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val plus by Sb.matrix.accountData.state.collectAsState()
    var roomPins by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(roomId) { roomPins = runCatching { room.roomInfo().pinnedEventIds }.getOrDefault(emptyList()) }
    val personal = plus.personalPins(roomId)
    fun label(id: String) = (items.firstOrNull { (it as? TEvent)?.eventId == id } as? TEvent)?.let { "${it.senderName}: ${it.plainText}" } ?: "Pinned message"
    val actions = (roomPins.map { id -> SheetAction(label(id), Icons.Filled.PushPin, subtitle = "Pinned for everyone") { onJump(id) } } +
        personal.flatMap { id ->
            listOf(
                SheetAction(label(id), Icons.Filled.PushPin, subtitle = "Pinned for you · tap to jump") { onJump(id) },
                SheetAction("Unpin for me", Icons.Outlined.Info, danger = true) { scope.launch { PlusEdit.setPersonalPin(roomId, id, false) } },
            )
        })
    ActionSheet(title = "Pinned messages", actions = actions, onDismiss = onDismiss)
}

fun handleLink(context: android.content.Context, url: String, nav: app.switchboard.ui.Nav) {
    val m = Regex("https://matrix\\.to/#/([@!#][^?]+)").find(url)
    if (m != null) {
        val target = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
        when {
            target.startsWith("@") -> { nav.push(Screen.UserProfile(target)); return }
            target.startsWith("!") && Sb.matrix.summary(target.substringBefore('/')) != null -> {
                val parts = target.split('/')
                nav.openChat(parts[0], parts.getOrNull(1)); return
            }
            else -> {
                app.switchboard.ui.settings.JoinRoomState.pending.value = url
                nav.push(Screen.JoinRoom); return
            }
        }
    }
    openUri(context, url)
}
