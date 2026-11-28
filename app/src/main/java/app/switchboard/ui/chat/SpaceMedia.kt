package app.switchboard.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Mxc
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.matrix.rustcomponents.sdk.DateDividerMode
import org.matrix.rustcomponents.sdk.RoomMessageEventMessageType
import org.matrix.rustcomponents.sdk.TimelineConfiguration
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineFilter
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineListener
import uniffi.matrix_sdk_ui.TimelineReadReceiptTracking

/** Photos, videos and reels from the chats of a space (or one chat), newest first. */
object MediaGallery {
    private val cache = HashMap<String, List<ViewerItem>>()

    fun cached(id: String) = cache[id]

    suspend fun load(id: String, onProgress: (Int, Int) -> Unit): List<ViewerItem> {
        val m = Sb.matrix
        val rooms = if (m.summary(id)?.isSpace == true) {
            HomeData.descendants(id, m.spaceChildren.value).filter { r -> m.summary(r)?.let { !it.isSpace && !it.isInvite } == true }
        } else listOf(id)
        val own = m.userId ?: ""
        val gate = Semaphore(3)
        var done = 0
        val all = coroutineScope {
            rooms.map { roomId ->
                async {
                    gate.withPermit {
                        val r = loadRoom(roomId, own)
                        done++; onProgress(done, rooms.size)
                        r
                    }
                }
            }.awaitAll().flatten()
        }
        val sorted = all.distinctBy { it.key }.sortedByDescending { it.ts }
        cache[id] = sorted
        return sorted
    }

    private suspend fun loadRoom(roomId: String, own: String): List<ViewerItem> {
        val room = Sb.matrix.room(roomId) ?: return emptyList()
        return try {
            val t = room.timelineWithConfiguration(
                TimelineConfiguration(
                    focus = TimelineFocus.Live(false),
                    filter = TimelineFilter.OnlyMessage(listOf(RoomMessageEventMessageType.IMAGE, RoomMessageEventMessageType.VIDEO)),
                    internalIdPrefix = "gallery",
                    dateDividerMode = DateDividerMode.DAILY,
                    trackReadReceipts = TimelineReadReceiptTracking.DISABLED,
                    reportUtds = false,
                ),
            )
            val items = ArrayList<TimelineItem>()
            val h = t.addListener(object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) {
                    synchronized(items) {
                        for (d in diff) when (d) {
                            is TimelineDiff.Append -> items.addAll(d.values)
                            is TimelineDiff.Reset -> { items.clear(); items.addAll(d.values) }
                            is TimelineDiff.PushFront -> items.add(0, d.value)
                            is TimelineDiff.PushBack -> items.add(d.value)
                            is TimelineDiff.Insert -> items.add(d.index.toInt().coerceIn(0, items.size), d.value)
                            is TimelineDiff.Set -> if (d.index.toInt() in items.indices) items[d.index.toInt()] = d.value
                            is TimelineDiff.Remove -> if (d.index.toInt() in items.indices) items.removeAt(d.index.toInt())
                            is TimelineDiff.Clear -> items.clear()
                            else -> {}
                        }
                    }
                }
            })
            runCatching { t.paginateBackwards(60u) }
            delay(300)
            h.cancel()
            // Timeline item keys are only unique within one chat, so key by chat and event.
            synchronized(items) { items.toList() }.mapNotNull { TimelineMapper.map(it, own) as? TEvent }
                .mapNotNull { it.toViewerItem(roomId) }
                .map { it.copy(key = roomId + "|" + (it.eventId ?: it.key)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpaceMediaGrid(spaceId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    val context = LocalContext.current
    var items by remember(spaceId) { mutableStateOf(MediaGallery.cached(spaceId)) }
    var progress by remember(spaceId) { mutableStateOf(0 to 0) }
    var viewer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(spaceId) {
        val fresh = MediaGallery.load(spaceId) { d, t -> progress = d to t }
        items = fresh
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        val list = items
        when {
            list == null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = c.accent)
                if (progress.second > 1) {
                    LinearProgressIndicator(progress = { progress.first / progress.second.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    Text("Looking through ${progress.second} chats…", style = Sbt.small, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos or videos yet.", style = Sbt.body, color = c.textMuted)
            }
            else -> LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(2.dp)) {
                items(list, key = { it.key }) { item ->
                    Box(
                        Modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(c.raised)
                            .combinedClickable(
                                onClick = { viewer = item.key },
                                onLongClick = { item.roomId?.let { nav.openChat(it, item.eventId) } },
                            ),
                    ) {
                        val src = item.thumb ?: item.source
                        AsyncImage(
                            model = remember(item.key) { ImageRequest.Builder(context).data(Mxc.of(src, 320, 320)).build() },
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isVideo) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp))
                        }
                        if (item.externalUrl != null) {
                            Text(
                                "REEL", fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 9.sp, color = c.onAccent,
                                modifier = Modifier.padding(5.dp).clip(RoundedCornerShape(4.dp)).background(c.accent).padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
        viewer?.let { key -> list?.let { MediaViewer(it, key) { viewer = null } } }
    }
}

@Composable
fun SpaceMediaScreen(spaceId: String) {
    val nav = LocalNav.current
    val name = Sb.matrix.summary(spaceId)?.let { Sb.matrix.accountData.plus.displayName(it) } ?: "Media"
    Column(Modifier.fillMaxSize().background(Sbt.c.background)) {
        ScreenHeader("Media", kicker = name, onBack = { nav.pop() })
        SpaceMediaGrid(spaceId)
    }
}
