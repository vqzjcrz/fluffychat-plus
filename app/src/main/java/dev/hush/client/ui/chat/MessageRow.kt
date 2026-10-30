package dev.hush.client.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.hush.client.data.MessageDensity
import dev.hush.client.discord.Attachment
import dev.hush.client.discord.Cdn
import dev.hush.client.discord.Embed
import dev.hush.client.discord.Message
import dev.hush.client.discord.NORMAL_MESSAGE_TYPES
import dev.hush.client.discord.Reaction
import dev.hush.client.discord.snowflakeTime
import dev.hush.client.ui.Fmt
import dev.hush.client.ui.components.UserAvatar
import dev.hush.client.ui.theme.LocalPalette
import dev.hush.client.ui.theme.LocalSettings

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "avif")

/** Whether [msg] continues the visual group started by [older]. */
fun startsGroup(msg: Message, older: Message?): Boolean {
    if (older == null) return true
    if (older.author.id != msg.author.id) return true
    if (msg.type !in NORMAL_MESSAGE_TYPES || older.type !in NORMAL_MESSAGE_TYPES) return true
    if (msg.type == 19) return true
    return snowflakeTime(msg.id) - snowflakeTime(older.id) > 7 * 60_000
}

@Composable
fun messageTextStyle(): TextStyle {
    val s = LocalSettings.current
    val p = LocalPalette.current
    val compact = s.density == MessageDensity.COMPACT
    return MaterialTheme.typography.bodyLarge.copy(
        fontSize = (if (compact) 14.sp else 15.sp) * s.fontScale,
        lineHeight = (if (compact) 19.sp else 21.sp) * s.fontScale,
        color = p.text,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    msg: Message,
    authorName: String,
    groupStart: Boolean,
    highlight: Boolean,
    resolver: MdResolver,
    replyName: (Message) -> String,
    onLongPress: () -> Unit,
    onOpenImage: (String) -> Unit,
    onJumpTo: (String) -> Unit,
    onReact: (Reaction) -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val compact = s.density == MessageDensity.COMPACT
    val showAvatar = !compact && s.showAvatars
    val textStyle = messageTextStyle()
    val accent = p.accent

    val background = if (highlight) {
        Modifier
            .background(accent.copy(alpha = 0.08f))
            .drawBehind { drawRect(color = accent, size = Size(3.dp.toPx(), size.height)) }
    } else {
        Modifier
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(background)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = if (groupStart) (if (compact) 8.dp else 12.dp) else 1.dp,
                bottom = 1.dp,
            ),
    ) {
        if (msg.type !in NORMAL_MESSAGE_TYPES) {
            SystemLine(msg, authorName)
            return@Column
        }

        if (groupStart && msg.type == 19) {
            ReplyPreview(
                ref = msg.referencedMessage,
                name = msg.referencedMessage?.let(replyName),
                indent = if (showAvatar) 52.dp else 0.dp,
                onClick = { msg.referencedMessage?.let { onJumpTo(it.id) } },
            )
        }

        Row {
            if (showAvatar) {
                if (groupStart) UserAvatar(msg.author, 40.dp) else Spacer(Modifier.width(40.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                if (groupStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName,
                            color = p.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textStyle.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (msg.author.bot) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "BOT",
                                color = p.onAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(p.accent)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Fmt.messageTime(msg.id, s.use24h, short = compact),
                            color = p.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.size(2.dp))
                }

                val soleEmbed = msg.embeds.singleOrNull()
                val hideText = soleEmbed != null && soleEmbed.type in setOf("image", "gifv") &&
                    msg.content.trim() == soleEmbed.url
                if (msg.content.isNotEmpty() && !hideText) {
                    MessageBody(
                        content = msg.content,
                        edited = msg.editedTimestamp != null,
                        resolver = resolver,
                        textStyle = textStyle,
                    )
                }

                msg.attachments.forEach { AttachmentView(it, onOpenImage) }

                msg.stickerItems.forEach { sticker ->
                    val url = Cdn.sticker(sticker.id, sticker.formatType)
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = sticker.name,
                            modifier = Modifier.padding(top = 4.dp).size(140.dp),
                        )
                    } else {
                        Text("[sticker: ${sticker.name}]", color = p.muted, fontSize = 13.sp)
                    }
                }

                if (s.showEmbeds) {
                    msg.embeds.forEach { EmbedView(it, resolver, onOpenImage) }
                }

                if (msg.reactions.isNotEmpty()) Reactions(msg.reactions, onReact)
            }
        }
    }
}

@Composable
private fun SystemLine(msg: Message, authorName: String) {
    val p = LocalPalette.current
    val action = when (msg.type) {
        1 -> "added ${msg.mentions.firstOrNull()?.displayName ?: "someone"} to the group."
        2 -> "removed ${msg.mentions.firstOrNull()?.displayName ?: "someone"} from the group."
        3 -> "started a call."
        4 -> "changed the channel name: ${msg.content}"
        5 -> "changed the channel icon."
        6 -> "pinned a message."
        7 -> "joined the server."
        8, 9, 10, 11 -> "boosted the server."
        12 -> "followed this channel."
        18 -> "started a thread: ${msg.content}"
        else -> msg.content.ifBlank { "did something." }
    }
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("→", color = p.success, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$authorName $action",
            color = p.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(Fmt.messageTime(msg.id, LocalSettings.current.use24h, short = false), color = p.muted.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun ReplyPreview(ref: Message?, name: String?, indent: Dp, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(start = indent, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("↱", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        if (ref == null) {
            Text("Original message was deleted", color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
        } else {
            UserAvatar(ref.author, 16.dp)
            Spacer(Modifier.width(5.dp))
            Text(name ?: ref.author.displayName, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Text(
                text = ref.content.replace('\n', ' ').ifBlank { if (ref.attachments.isNotEmpty()) "Attachment" else "…" },
                color = p.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AttachmentView(a: Attachment, onOpenImage: (String) -> Unit) {
    val s = LocalSettings.current
    val ext = a.filename.substringAfterLast('.', "").lowercase()
    val isImage = a.contentType?.startsWith("image/") == true || ext in IMAGE_EXTENSIONS
    if (isImage && s.showMedia) {
        MediaImage(url = a.proxyUrl ?: a.url, width = a.width, height = a.height) { onOpenImage(a.url) }
    } else {
        FileCard(a)
    }
}

@Composable
fun MediaImage(url: String, width: Int?, height: Int?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val maxW = 280.dp
    val maxH = 300.dp
    val (w, h) = if (width != null && height != null && width > 0 && height > 0) {
        val ratio = width.toFloat() / height
        var dw = maxW
        var dh = dw / ratio
        if (dh > maxH) {
            dh = maxH
            dw = dh * ratio
        }
        dw to dh
    } else {
        maxW to 180.dp
    }
    val density = LocalDensity.current
    val sizedUrl = with(density) { Cdn.sized(url, w.roundToPx(), h.roundToPx()) }
    AsyncImage(
        model = sizedUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(top = 6.dp)
            .size(w, h)
            .clip(RoundedCornerShape(10.dp))
            .background(p.elevated)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun FileCard(a: Attachment) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    val ext = a.filename.substringAfterLast('.', "file").take(4).uppercase()
    Row(
        Modifier
            .padding(top = 6.dp)
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.elevated)
            .border(1.dp, p.divider, RoundedCornerShape(10.dp))
            .clickable { runCatching { uri.openUri(a.url) } }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ext,
            color = p.onAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(p.accent)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(a.filename, color = p.link, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.fileSize(a.size), color = p.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmbedView(e: Embed, resolver: MdResolver, onOpenImage: (String) -> Unit) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val uri = LocalUriHandler.current

    // Image / GIF links render as bare media.
    if (e.type == "image" || e.type == "gifv") {
        val media = e.thumbnail ?: e.image ?: return
        val src = media.proxyUrl ?: media.url ?: return
        if (s.showMedia) MediaImage(src, media.width, media.height) { onOpenImage(media.url ?: src) }
        return
    }
    if (e.title == null && e.description == null && e.fields.isEmpty() && e.image == null && e.thumbnail == null) return

    val bar = e.color?.let { Color(0xFF000000.toInt() or it) } ?: p.divider
    val small = messageTextStyle().let { it.copy(fontSize = it.fontSize * 0.92f, lineHeight = it.lineHeight * 0.92f) }

    Column(
        Modifier
            .padding(top = 6.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.elevated)
            .drawBehind { drawRect(color = bar, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        e.provider?.name?.let { Text(it, color = p.muted, fontSize = 11.sp) }
        e.author?.name?.let { Text(it, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        e.title?.let { title ->
            val url = e.url
            Text(
                text = title,
                color = if (url != null) p.link else p.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = if (url != null) Modifier.clickable { runCatching { uri.openUri(url) } } else Modifier,
            )
        }
        e.description?.let { MessageBody(it, edited = false, resolver = resolver, textStyle = small) }
        e.fields.forEach { f ->
            Column {
                Text(f.name, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                MessageBody(f.value, edited = false, resolver = resolver, textStyle = small)
            }
        }
        if (s.showMedia) {
            val img = e.image ?: e.thumbnail
            val src = img?.proxyUrl ?: img?.url
            if (img != null && src != null) MediaImage(src, img.width, img.height) { onOpenImage(img.url ?: src) }
        }
        e.footer?.text?.let { Text(it, color = p.muted, fontSize = 11.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reactions(reactions: List<Reaction>, onReact: (Reaction) -> Unit) {
    val p = LocalPalette.current
    val animate = LocalSettings.current.animateMedia
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        reactions.forEach { r ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (r.me) p.accent.copy(alpha = 0.18f) else p.elevated)
                    .border(1.dp, if (r.me) p.accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onReact(r) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val emojiId = r.emoji.id
                if (emojiId != null) {
                    AsyncImage(
                        model = Cdn.emoji(emojiId, r.emoji.animated, animate),
                        contentDescription = r.emoji.name,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(r.emoji.name ?: "?", fontSize = 15.sp)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = r.count.toString(),
                    color = if (r.me) p.accent else p.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
