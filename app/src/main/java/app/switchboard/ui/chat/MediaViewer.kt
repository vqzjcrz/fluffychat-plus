package app.switchboard.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.switchboard.core.MediaFiles
import app.switchboard.core.Mxc
import app.switchboard.ui.AppState
import app.switchboard.ui.theme.Inter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.MediaSource
import java.io.File
import kotlin.math.abs

/** One picture or video the viewer can show. */
data class ViewerItem(
    val key: String,
    val source: MediaSource,
    val thumb: MediaSource?,
    val isVideo: Boolean,
    val mime: String?,
    val filename: String,
    val sender: String,
    val ts: Long,
    val roomId: String?,
    val eventId: String?,
    val externalUrl: String? = null,
)

fun TEvent.toViewerItem(roomId: String?): ViewerItem? = when (val b = body) {
    is Body.Image -> ViewerItem(key, b.source, b.thumb, false, b.mime, b.filename, senderName, ts, roomId, eventId)
    is Body.Video -> ViewerItem(key, b.source, b.thumb, true, b.mime, b.filename, senderName, ts, roomId, eventId)
    is Body.Reel -> ViewerItem(key, b.source, b.thumb, true, b.mime, b.filename, b.author, ts, roomId, eventId, b.url)
    is Body.Sticker -> ViewerItem(key, b.source, null, false, "image/png", b.body, senderName, ts, roomId, eventId)
    else -> null
}

@Composable
fun MediaViewer(items: List<ViewerItem>, startKey: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = items.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { items.size }
    val dragY = remember { Animatable(0f) }
    var chrome by remember { mutableStateOf(true) }
    BackHandler(onBack = onClose)
    val alpha = (1f - abs(dragY.value) / 1200f).coerceIn(0.2f, 1f)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha))) {
        HorizontalPager(
            state = pager, beyondViewportPageCount = 1, key = { items[it].key },
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { translationY = dragY.value }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(dragY.value) > 260) onClose()
                            else scope.launch { dragY.animateTo(0f, spring()) }
                        },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { dragY.snapTo(dragY.value + amount) }
                    }
                },
        ) { page ->
            val item = items[page]
            if (item.isVideo) VideoPage(item, active = pager.currentPage == page) { chrome = !chrome }
            else ImagePage(item) { chrome = !chrome }
        }
        if (chrome) {
            val item = items.getOrNull(pager.currentPage)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Filled.Close, "Close", onClose)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (item != null) {
                        Text(item.sender, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(formatTime(item.ts), color = Color.White.copy(alpha = 0.7f), fontFamily = Inter, maxLines = 1)
                    }
                }
                if (item?.externalUrl != null) RoundIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open on Instagram") { openUri(context, item.externalUrl) }
                if (item != null) {
                    RoundIcon(Icons.Outlined.Share, "Share") { scope.launch { shareMedia(context, item) } }
                    RoundIcon(Icons.Outlined.Download, "Save") { scope.launch { saveMedia(context, item) } }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White)
    }
}

@Composable
private fun ImagePage(item: ViewerItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (scale > 1f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f })
            }
            .pointerInput(Unit) {
                // Pinch to zoom; pan only while zoomed, so swiping between items and down to close still work.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) { ox += pan.x; oy += pan.y } else { ox = 0f; oy = 0f }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = remember(item.key) { ImageRequest.Builder(context).data(Mxc.of(item.source)).placeholderMemoryCacheKey(item.thumb?.let { Mxc.of(it, 640, 640).key }).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy },
        )
    }
}

@Composable
private fun VideoPage(item: ViewerItem, active: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.key) {
        try { file = MediaFiles.file(item.source, item.filename, item.mime ?: "video/mp4", context) } catch (e: Exception) { error = e.message }
    }
    val player = remember(item.key) { ExoPlayer.Builder(context).build().apply { repeatMode = ExoPlayer.REPEAT_MODE_ONE } }
    DisposableEffect(item.key) { onDispose { player.release() } }
    LaunchedEffect(file) {
        file?.let { player.setMediaItem(MediaItem.fromUri(Uri.fromFile(it))); player.prepare() }
    }
    LaunchedEffect(active, file) { player.playWhenReady = active && file != null }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text("Couldn't load the video", color = Color.White)
            file == null -> {
                if (item.thumb != null) AsyncImage(
                    model = remember(item.key) { ImageRequest.Builder(context).data(Mxc.of(item.thumb, 640, 640)).build() },
                    contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                )
                CircularProgressIndicator(color = Color.White)
            }
            else -> AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private suspend fun shareMedia(context: Context, item: ViewerItem) {
    try {
        val f = MediaFiles.file(item.source, item.filename, item.mime ?: "application/octet-stream", context)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, item.filename.ifBlank { "media" }.replace('/', '_'))
        f.copyTo(out, overwrite = true)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) { AppState.nav.toast("Couldn't share: ${e.message}") }
}

/** Saves a picture or video to the phone's gallery (Pictures/Switchboard or Movies/Switchboard). */
suspend fun saveMedia(context: Context, item: ViewerItem) {
    try {
        val f = MediaFiles.file(item.source, item.filename, item.mime ?: "application/octet-stream", context)
        val mime = item.mime ?: if (item.isVideo) "video/mp4" else "image/jpeg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.filename.ifBlank { "switchboard_${item.ts}" })
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (item.isVideo) "Movies" else "Pictures") + "/Switchboard")
        }
        val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values) ?: throw IllegalStateException("No storage")
        context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        AppState.nav.toast("Saved to ${if (item.isVideo) "Movies" else "Pictures"}/Switchboard")
    } catch (e: Exception) { AppState.nav.toast("Couldn't save: ${e.message}") }
}
