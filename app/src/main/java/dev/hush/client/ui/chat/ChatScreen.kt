package dev.hush.client.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.hush.client.discord.ChannelKind
import dev.hush.client.discord.ChannelUi
import dev.hush.client.discord.Message
import dev.hush.client.discord.ReactionEmoji
import dev.hush.client.discord.Typist
import dev.hush.client.discord.UploadFile
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.Fmt
import dev.hush.client.ui.components.RemoteIcon
import dev.hush.client.ui.components.avatarShape
import dev.hush.client.ui.theme.LocalPalette
import dev.hush.client.ui.theme.LocalSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, channelId: String) {
    val p = LocalPalette.current
    val settings = LocalSettings.current
    val guilds by vm.guilds.collectAsStateWithLifecycle()
    val dms by vm.dms.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val channel = remember(guilds, dms, channelId) { vm.findChannel(channelId) }
    val guildId = channel?.guildId

    val messagesFlow = remember(channelId) { vm.messages(channelId) }
    val state by messagesFlow.collectAsStateWithLifecycle()
    val typingFlow = remember(channelId) { vm.typing(channelId) }
    val typing by typingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val resolver = remember(guildId, settings.use24h) { vm.resolver(guildId, settings.use24h) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var draft by rememberSaveable(channelId) { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Message?>(null) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(channelId) {
        vm.enterChannel(channelId)
        onDispose { vm.leaveChannel(channelId) }
    }

    val editing = vm.editing
    LaunchedEffect(editing?.id) {
        if (editing != null) draft = editing.content
    }

    // Page in older history as the user nears the top.
    LaunchedEffect(listState, channelId) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) -> if (total > 0 && last >= total - 6) vm.loadOlder(channelId) }
    }

    // Follow new messages when already at the bottom.
    val newestId = state.list.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 10 } }

    Column(Modifier.fillMaxSize().background(p.background)) {
        ChatHeader(channel, onBack = { vm.back() })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 10.dp),
            ) {
                itemsIndexed(state.list, key = { _, m -> m.id }) { index, msg ->
                    val older = state.list.getOrNull(index + 1)
                    val day = Fmt.day(msg.id)
                    val newDay = older == null || Fmt.day(older.id) != day
                    val groupStart = newDay || startsGroup(msg, older)
                    val myId = me?.id
                    val highlight = myId != null && msg.author.id != myId && (
                        msg.mentionEveryone ||
                            msg.mentions.any { it.id == myId } ||
                            msg.referencedMessage?.author?.id == myId
                        )
                    Column {
                        if (newDay && (older != null || !state.hasMore)) DayDivider(Fmt.dayLabel(day))
                        MessageRow(
                            msg = msg,
                            authorName = vm.displayName(msg, guildId),
                            groupStart = groupStart,
                            highlight = highlight,
                            resolver = resolver,
                            replyName = { vm.displayName(it, guildId) },
                            onLongPress = { actionTarget = msg },
                            onOpenImage = { viewerUrl = it },
                            onJumpTo = { id ->
                                val target = state.list.indexOfFirst { it.id == id }
                                if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                            },
                            onReact = { r -> vm.toggleReaction(msg, r.emoji) },
                        )
                    }
                }
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = p.accent, strokeWidth = 2.dp)
                        }
                    }
                } else if (state.loaded && !state.hasMore) {
                    item(key = "start") { ChannelStart(channel) }
                }
            }

            val error = state.error
            if (error != null && state.list.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = p.muted)
                    TextButton(onClick = { vm.retry(channelId) }) { Text("Retry", color = p.accent) }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showJump,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.accent)
                        .clickable { scope.launch { listState.animateScrollToItem(0) } }
                        .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Jump to present", color = p.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }

        TypingLine(typing)

        if (channel == null || channel.canSend) {
            Composer(
                vm = vm,
                channelId = channelId,
                label = channel?.let { if (it.kind == ChannelKind.DM || it.kind == ChannelKind.GROUP_DM) "@${it.name}" else "#${it.name}" } ?: "",
                guildId = guildId,
                draft = draft,
                onDraft = { draft = it },
            )
        } else {
            Text(
                text = "You can't send messages in this channel.",
                color = p.muted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            )
        }
    }

    actionTarget?.let { target ->
        val mine = target.author.id == me?.id
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = p.surface,
            contentColor = p.text,
        ) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(p.elevated)
                                .clickable {
                                    vm.toggleReaction(target, ReactionEmoji(name = emoji))
                                    actionTarget = null
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 22.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SheetAction("Reply") {
                    vm.startReply(target)
                    actionTarget = null
                }
                if (mine && target.type in setOf(0, 19)) {
                    SheetAction("Edit") {
                        vm.startEdit(target)
                        actionTarget = null
                    }
                }
                if (target.content.isNotEmpty()) {
                    SheetAction("Copy text") {
                        clipboard.setText(AnnotatedString(target.content))
                        actionTarget = null
                    }
                }
                SheetAction("Copy message link") {
                    clipboard.setText(AnnotatedString("https://discord.com/channels/${guildId ?: "@me"}/${target.channelId}/${target.id}"))
                    actionTarget = null
                }
                if (mine) {
                    SheetAction("Delete", danger = true) {
                        confirmDelete = target
                        actionTarget = null
                    }
                }
            }
        }
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = p.surface,
            title = { Text("Delete message?", color = p.text) },
            text = { Text("This can't be undone.", color = p.muted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target)
                    confirmDelete = null
                }) { Text("Delete", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = p.text) }
            },
        )
    }

    viewerUrl?.let { url -> ImageViewer(url) { viewerUrl = null } }
}

@Composable
private fun ChatHeader(channel: ChannelUi?, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
            }
            val isDm = channel?.kind == ChannelKind.DM || channel?.kind == ChannelKind.GROUP_DM
            if (isDm && channel != null) {
                RemoteIcon(channel.iconUrl, channel.name, 30.dp, avatarShape())
                Spacer(Modifier.width(10.dp))
            } else {
                Text("#", color = p.muted, fontSize = 22.sp, modifier = Modifier.padding(end = 6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = channel?.name ?: "Loading…",
                    color = p.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val topic = channel?.topic?.replace('\n', ' ')?.takeIf { it.isNotBlank() }
                if (topic != null) {
                    Text(topic, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = p.divider, thickness = 0.5.dp)
    }
}

@Composable
private fun DayDivider(label: String) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = p.divider)
        Text(label, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f), color = p.divider)
    }
}

@Composable
private fun ChannelStart(channel: ChannelUi?) {
    val p = LocalPalette.current
    val isDm = channel?.kind == ChannelKind.DM || channel?.kind == ChannelKind.GROUP_DM
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(p.elevated),
            contentAlignment = Alignment.Center,
        ) {
            if (isDm && channel != null) {
                RemoteIcon(channel.iconUrl, channel.name, 64.dp, CircleShape)
            } else {
                Text("#", color = p.text, fontSize = 34.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isDm) channel?.name.orEmpty() else "Welcome to #${channel?.name.orEmpty()}",
            color = p.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isDm) "This is the beginning of your conversation." else "This is the start of the channel.",
            color = p.muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TypingLine(typing: List<Typist>) {
    val p = LocalPalette.current
    val text = when (typing.size) {
        0 -> null
        1 -> "${typing[0].name} is typing…"
        2 -> "${typing[0].name} and ${typing[1].name} are typing…"
        3 -> "${typing[0].name}, ${typing[1].name} and ${typing[2].name} are typing…"
        else -> "Several people are typing…"
    }
    AnimatedVisibility(visible = text != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(
            text = text.orEmpty(),
            color = p.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(p.background).padding(horizontal = 18.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text = label,
        color = if (danger) p.danger else p.text,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun Composer(
    vm: AppViewModel,
    channelId: String,
    label: String,
    guildId: String?,
    draft: String,
    onDraft: (String) -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(uris)
    }
    val reply = vm.replyTo
    val editing = vm.editing
    val canSubmit = !vm.sending && (draft.isNotBlank() || (editing == null && vm.attachments.isNotEmpty()))

    Column(
        Modifier
            .fillMaxWidth()
            .background(p.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
    ) {
        if (reply != null) {
            ContextBar(text = "Replying to ${vm.displayName(reply, guildId)}", onClose = { vm.replyTo = null }) {
                Text(
                    text = if (vm.replyMention) "@ ON" else "@ OFF",
                    color = if (vm.replyMention) p.accent else p.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { vm.replyMention = !vm.replyMention }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (editing != null) {
            ContextBar(text = "Editing message", onClose = {
                vm.editing = null
                onDraft("")
            })
        }
        if (vm.attachments.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.attachments.toList()) { file -> AttachmentChip(file) { vm.attachments.remove(file) } }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (editing == null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(p.elevated)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Attach", tint = p.muted)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.elevated)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val style = TextStyle(color = p.text, fontSize = 15.sp * s.fontScale, lineHeight = 20.sp * s.fontScale)
                if (draft.isEmpty()) {
                    Text("Message $label", style = style.copy(color = p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        onDraft(it)
                        if (it.isNotEmpty()) vm.onTyping(channelId)
                    },
                    textStyle = style,
                    cursorBrush = SolidColor(p.accent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = canSubmit || vm.sending,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(p.accent)
                            .clickable(enabled = canSubmit) {
                                val text = draft
                                onDraft("")
                                vm.submit(channelId, text) { failed -> onDraft(failed) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (vm.sending) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = p.onAccent, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (editing != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (editing != null) "Save" else "Send",
                                tint = p.onAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        if (draft.length > 1800) {
            Text(
                text = "${draft.length} / 2000",
                color = if (draft.length > 2000) p.danger else p.muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun ContextBar(text: String, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = p.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AttachmentChip(file: UploadFile, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(p.elevated)) {
        if (file.mime.startsWith("image/") && file.uri != null) {
            AsyncImage(model = file.uri, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = file.name,
                color = p.muted,
                fontSize = 10.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Open", color = Color.White) }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
