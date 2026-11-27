package app.switchboard.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Mxc
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val timeFmt get() = SimpleDateFormat("h:mm a", Locale.getDefault())
fun formatTime(ts: Long): String = timeFmt.format(Date(ts))

/** What tapping parts of a message does. */
class MessageCallbacks(
    val onLongPress: (TEvent) -> Unit,
    val onReply: (TEvent) -> Unit,
    val onOpenMedia: (TEvent) -> Unit,
    val onReact: (TEvent, String) -> Unit,
    val onJumpTo: (String) -> Unit,
    val onLink: (String) -> Unit,
    val onSender: (String) -> Unit,
    val onRetry: (TEvent) -> Unit,
    val onVote: (TEvent, String) -> Unit,
    val onOpenPanel: () -> Unit = {},
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageItem(
    ev: TEvent,
    prev: TEvent?,
    next: TEvent?,
    isDirect: Boolean,
    highlighted: Boolean,
    cb: MessageCallbacks,
) {
    val c = Sbt.c
    if (!ev.isMessage) {
        Text(
            (ev.body as Body.Notice).text, style = Sbt.tiny, color = c.textFaint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }
    val sameAsPrev = prev != null && prev.isMessage && prev.sender == ev.sender && ev.ts - prev.ts < 5 * 60_000
    val sameAsNext = next != null && next.isMessage && next.sender == ev.sender && next.ts - ev.ts < 5 * 60_000
    val own = ev.isOwn
    val swipeLeftToRight by Prefs.swipeToReplyLeftToRight.collectAsState()
    var dragX by remember { mutableFloatStateOf(0f) }
    var otherX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }

    Column(
        Modifier.fillMaxWidth()
            .background(if (highlighted) c.accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(start = 14.dp, end = 14.dp, top = if (sameAsPrev) 2.dp else 10.dp, bottom = if (sameAsNext) 1.dp else 4.dp)
            .pointerInput(ev.key, swipeLeftToRight) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragX) > threshold && ev.canReply) cb.onReply(ev)
                        else if (!swipeLeftToRight && otherX > threshold * 1.3f) cb.onOpenPanel()
                        dragX = 0f; otherX = 0f
                    },
                    onDragCancel = { dragX = 0f; otherX = 0f },
                ) { change, amount ->
                    val next2 = dragX + amount
                    val allowed = if (swipeLeftToRight) next2 >= 0 else next2 <= 0
                    if (allowed) dragX = next2.coerceIn(-threshold * 1.4f, threshold * 1.4f)
                    else { dragX = 0f; otherX += amount }
                    change.consume()
                }
            },
        horizontalAlignment = if (own) Alignment.End else Alignment.Start,
    ) {
        if (!own && !sameAsPrev && !isDirect) {
            Text(
                ev.senderName.uppercase(), style = Sbt.kicker, color = c.textMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp).clickable { cb.onSender(ev.sender) },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.offset { IntOffset(dragX.roundToInt(), 0) }) {
            if (abs(dragX) > 8) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply, null, tint = c.textMuted,
                    modifier = Modifier.align(if (dragX > 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .offset(x = if (dragX > 0) (-36).dp else 36.dp).size(22.dp),
                )
            }
            BubbleContent(ev, own, sameAsPrev, sameAsNext, cb)
        }
        if (ev.reactions.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 4.dp).widthIn(max = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, if (own) Alignment.End else Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ev.reactions.forEach { r ->
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (r.mine) c.accent.copy(alpha = 0.22f) else c.raised)
                            .border(1.dp, if (r.mine) c.accent.copy(alpha = 0.6f) else c.line, RoundedCornerShape(14.dp))
                            .clickable { cb.onReact(ev, r.key) }
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.key.take(16), fontSize = 14.sp)
                        if (r.count > 1) { Spacer(Modifier.width(4.dp)); Text(r.count.toString(), style = Sbt.tiny, color = c.text) }
                    }
                }
            }
        }
        if (!sameAsNext || ev.failed) Footer(ev, own, cb)
    }
}

@Composable
private fun Footer(ev: TEvent, own: Boolean, cb: MessageCallbacks) {
    val c = Sbt.c
    val receipts by Prefs.showReadReceipts.collectAsState()
    Row(Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ev.failed) {
            Icon(Icons.Filled.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Not sent · Tap to retry", style = Sbt.tiny, color = c.danger, modifier = Modifier.clickable { cb.onRetry(ev) })
            return@Row
        }
        Text(formatTime(ev.ts) + if (ev.edited) " · edited" else "", style = Sbt.tiny, color = c.textFaint)
        if (own) {
            Spacer(Modifier.width(4.dp))
            when {
                ev.sending -> Icon(Icons.Filled.Schedule, "Sending", tint = c.textFaint, modifier = Modifier.size(12.dp))
                receipts && ev.readBy.isNotEmpty() -> Text("· Read", style = Sbt.tiny, color = c.textFaint)
                else -> Icon(Icons.Filled.Done, "Sent", tint = c.textFaint, modifier = Modifier.size(12.dp))
            }
        }
    }
}

private fun bubbleShape(own: Boolean, top: Boolean, bottom: Boolean): RoundedCornerShape {
    val big = 16.dp; val small = 5.dp
    return if (own) RoundedCornerShape(topStart = big, topEnd = if (top) small else big, bottomEnd = if (bottom) small else big, bottomStart = big)
    else RoundedCornerShape(topStart = if (top) small else big, topEnd = big, bottomEnd = big, bottomStart = if (bottom) small else big)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleContent(ev: TEvent, own: Boolean, sameAsPrev: Boolean, sameAsNext: Boolean, cb: MessageCallbacks) {
    val c = Sbt.c
    val shape = bubbleShape(own, sameAsPrev, sameAsNext)
    val bg = if (own) c.ownBubble else c.bubbleIn
    val fg = if (own) c.onOwnBubble else c.text
    val tap: () -> Unit = {}
    val long = { cb.onLongPress(ev) }
    val bubbleMod = Modifier.widthIn(max = 310.dp).clip(shape)
        .then(if (c.outlineBubbles && !own) Modifier.border(1.dp, c.line, shape) else Modifier)
    when (val b = ev.body) {
        is Body.Text -> {
            if (b.onlyEmoji && ev.reply == null) {
                Text(b.text, fontSize = 40.sp, modifier = Modifier.combinedClickable(onClick = tap, onLongClick = long))
                return
            }
            Column(bubbleMod.background(bg).combinedClickable(onClick = tap, onLongClick = long).padding(horizontal = 14.dp, vertical = 10.dp)) {
                ev.reply?.let { ReplyQuote(it, own, cb) }
                MessageText(b, own, fg, cb)
            }
        }
        is Body.Image -> Column(bubbleMod.background(bg)) {
            ev.reply?.let { Box(Modifier.padding(10.dp)) { ReplyQuote(it, own, cb) } }
            MediaThumb(b.thumb ?: b.source, b.w, b.h, animated = b.animated, full = b.source, onClick = { cb.onOpenMedia(ev) }, onLong = long)
            b.caption?.takeIf { it.isNotBlank() && it != b.filename }?.let {
                Text(it, style = Sbt.body, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        is Body.Video -> Column(bubbleMod.background(bg)) {
            ev.reply?.let { Box(Modifier.padding(10.dp)) { ReplyQuote(it, own, cb) } }
            Box(contentAlignment = Alignment.Center) {
                if (b.thumb != null) MediaThumb(b.thumb, b.w, b.h, onClick = { cb.onOpenMedia(ev) }, onLong = long)
                else Box(Modifier.size(240.dp, 160.dp).background(Color.Black).combinedClickable(onClick = { cb.onOpenMedia(ev) }, onLongClick = long))
                PlayBadge()
                if (b.durationMs > 0) Text(
                    duration(b.durationMs), style = Sbt.tiny, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            b.caption?.takeIf { it.isNotBlank() && it != b.filename }?.let {
                Text(it, style = Sbt.body, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        is Body.Reel -> ReelCard(b, onClick = { cb.onOpenMedia(ev) }, onLong = long)
        is Body.Sticker -> MediaThumb(b.source, b.w.coerceAtMost(200), b.h.coerceAtMost(200), maxW = 180, onClick = {}, onLong = long, bubble = false)
        is Body.Audio -> Box(bubbleMod.background(bg).combinedClickable(onClick = tap, onLongClick = long).padding(10.dp)) { AudioRow(ev.key, b, fg) }
        is Body.File -> FileRow(b, fg, bubbleMod.background(bg), long)
        is Body.Location -> {
            val context = LocalContext.current
            Row(bubbleMod.background(bg).combinedClickable(onClick = { openUri(context, b.geoUri) }, onLongClick = long).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = fg); Spacer(Modifier.width(8.dp))
                Text(b.body.ifBlank { "Location" }, style = Sbt.body, color = fg)
            }
        }
        is Body.Poll -> PollView(ev, b, bubbleMod.background(bg), fg, cb)
        Body.Redacted -> Text("Message deleted", style = Sbt.body, fontStyle = FontStyle.Italic, color = c.textFaint,
            modifier = bubbleMod.border(1.dp, c.line, shape).combinedClickable(onClick = tap, onLongClick = long).padding(horizontal = 14.dp, vertical = 9.dp))
        Body.Encrypted -> Text("Can't decrypt this message yet", style = Sbt.body, fontStyle = FontStyle.Italic, color = c.textFaint,
            modifier = bubbleMod.border(1.dp, c.line, shape).combinedClickable(onClick = tap, onLongClick = long).padding(horizontal = 14.dp, vertical = 9.dp))
        is Body.Notice -> {}
    }
}

@Composable
private fun MessageText(b: Body.Text, own: Boolean, fg: Color, cb: MessageCallbacks) {
    val c = Sbt.c
    val colors = TextColors(
        text = fg,
        link = if (own) c.ownLink else c.accent,
        mention = if (own) c.ownLink else c.accent,
        mentionMe = if (own) c.onOwnBubble else c.accent,
        codeBg = fg.copy(alpha = 0.10f),
        quote = fg.copy(alpha = 0.75f),
    )
    val own2 = Sb.matrix.userId
    val text = remember(b, colors) { RichText.render(b.text, b.html, colors, own2) { cb.onLink(it) } }
    Text(
        if (b.emote) androidx.compose.ui.text.buildAnnotatedString { append("* "); append(text) } else text,
        style = Sbt.body.copy(fontStyle = if (b.emote) FontStyle.Italic else FontStyle.Normal),
        color = if (b.notice) fg.copy(alpha = 0.8f) else fg,
    )
}

@Composable
private fun ReplyQuote(r: ReplyPreview, own: Boolean, cb: MessageCallbacks) {
    val c = Sbt.c
    val fg = if (own) c.onOwnBubble else c.text
    Row(
        Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.08f))
            .clickable { cb.onJumpTo(r.eventId) }.height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (own) c.onOwnBubble.copy(alpha = 0.6f) else c.accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            if (r.sender.isNotEmpty()) Text(r.sender, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = fg.copy(alpha = 0.9f), maxLines = 1)
            Text(r.text, fontSize = 13.sp, color = fg.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumb(
    source: org.matrix.rustcomponents.sdk.MediaSource,
    w: Int, h: Int,
    animated: Boolean = false,
    full: org.matrix.rustcomponents.sdk.MediaSource? = null,
    maxW: Int = 280,
    onClick: () -> Unit,
    onLong: () -> Unit,
    bubble: Boolean = true,
) {
    val context = LocalContext.current
    val autoplay by Prefs.autoplayGifs.collectAsState()
    val ratio = if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.45f, 2.2f) else 4f / 3f
    val useFull = animated && autoplay && full != null
    val model = remember(source, useFull) {
        ImageRequest.Builder(context).data(if (useFull) Mxc.of(full!!) else Mxc.of(source, 640, 640)).build()
    }
    Box(Modifier.widthIn(max = maxW.dp).aspectRatio(ratio).heightIn(max = 380.dp).background(if (bubble) Color.Black.copy(alpha = 0.2f) else Color.Transparent)) {
        AsyncImage(
            model = model, contentDescription = null,
            contentScale = if (bubble) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().combinedClickable(onClick = onClick, onLongClick = onLong),
        )
    }
}

@Composable
private fun PlayBadge() {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/** A reel as a tall 9:16 card, like a reel shared in an Instagram DM. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelCard(b: Body.Reel, onClick: () -> Unit, onLong: () -> Unit) {
    val c = Sbt.c
    val context = LocalContext.current
    Box(
        Modifier.width(220.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A1E))
            .border(1.dp, c.line, RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        if (b.thumb != null) {
            AsyncImage(
                model = remember(b.thumb) { ImageRequest.Builder(context).data(Mxc.of(b.thumb, 480, 860)).build() },
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
        Text(
            "REEL", fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, color = c.onAccent,
            modifier = Modifier.padding(10.dp).clip(RoundedCornerShape(5.dp)).background(c.accent).padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Box(Modifier.align(Alignment.Center)) { PlayBadge() }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(b.author, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (b.origin != null) Text(b.origin, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            if (b.caption.isNotBlank()) Text(b.caption, fontSize = 13.sp, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AudioRow(key: String, b: Body.Audio, fg: Color) {
    val context = LocalContext.current
    val st by AudioPlayer.state.collectAsState()
    val mine = st.key == key
    val total = if (mine && st.durationMs > 0) st.durationMs else b.durationMs
    val progress = if (mine && total > 0) st.positionMs.toFloat() / total else 0f
    Row(Modifier.width(240.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f))
                .clickable { AudioPlayer.toggle(context, key, b.source, b.mime, b.filename) },
            contentAlignment = Alignment.Center,
        ) {
            if (mine && st.loading) CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
            else Icon(if (mine && st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Waveform(b.waveform, progress, fg) { f -> if (mine) AudioPlayer.seek(f) }
            Text(
                if (mine && st.positionMs > 0) duration(st.positionMs) else duration(total) + if (b.voice) "" else " · ${b.filename}",
                style = Sbt.tiny, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Waveform(samples: List<Float>, progress: Float, color: Color, onSeek: (Float) -> Unit) {
    val bars = remember(samples) {
        val n = 36
        if (samples.isEmpty()) List(n) { 0.35f + 0.25f * kotlin.math.sin(it * 0.7f).let { s -> abs(s) } }
        else List(n) { i ->
            val from = i * samples.size / n; val to = ((i + 1) * samples.size / n).coerceAtLeast(from + 1)
            samples.subList(from, to.coerceAtMost(samples.size)).maxOrNull()?.coerceIn(0.08f, 1f) ?: 0.1f
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
        detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
    }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { i, v ->
                val played = i / bars.size.toFloat() < progress
                Box(Modifier.width(3.dp).fillMaxHeight(v).clip(RoundedCornerShape(2.dp)).background(if (played) color else color.copy(alpha = 0.35f)))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(b: Body.File, fg: Color, mod: Modifier, onLong: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        mod.combinedClickable(onClick = {
            scope.launch { openAttachment(context, b.source, b.filename, b.mime ?: "application/octet-stream") }
        }, onLongClick = onLong).padding(12.dp).widthIn(min = 200.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Description, null, tint = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(b.filename, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.size > 0) Text(sizeText(b.size), style = Sbt.tiny, color = fg.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun PollView(ev: TEvent, b: Body.Poll, mod: Modifier, fg: Color, cb: MessageCallbacks) {
    val own = Sb.matrix.userId
    val total = b.votes.values.sumOf { it.size }.coerceAtLeast(1)
    Column(mod.padding(12.dp).widthIn(min = 220.dp)) {
        Text(b.question, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg)
        Spacer(Modifier.height(8.dp))
        b.answers.forEach { (id, text) ->
            val votes = b.votes[id]?.size ?: 0
            val mine = b.votes[id]?.contains(own) == true
            Box(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.08f))
                    .then(if (!b.ended) Modifier.clickable { cb.onVote(ev, id) } else Modifier),
            ) {
                Box(Modifier.fillMaxWidth(votes / total.toFloat()).height(36.dp).background(fg.copy(alpha = if (mine) 0.28f else 0.14f)))
                Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(votes.toString(), color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }
        if (b.ended) Text("Poll ended", style = Sbt.tiny, color = fg.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
    }
}

fun duration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.1f GB".format(bytes / 1073741824.0)
}

fun openUri(context: Context, uri: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Downloads an attachment and opens it in another app. */
suspend fun openAttachment(context: Context, source: org.matrix.rustcomponents.sdk.MediaSource, name: String, mime: String) {
    try {
        val f = app.switchboard.core.MediaFiles.file(source, name, mime, context)
        val shared = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        val out = java.io.File(shared, name.replace('/', '_'))
        f.copyTo(out, overwrite = true)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(i, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        app.switchboard.ui.AppState.nav.toast("Couldn't open the file: ${e.message}")
    }
}
