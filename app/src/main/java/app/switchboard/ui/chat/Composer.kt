package app.switchboard.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RoomMember
import java.io.File

/** What the composer is doing besides writing a new message. */
class ComposerState {
    var text by mutableStateOf(TextFieldValue(""))
    var replyTo by mutableStateOf<TEvent?>(null)
    var editing by mutableStateOf<TEvent?>(null)
    /** Display names inserted as mentions, with their user IDs. */
    val mentions = mutableStateListOf<Pair<String, String>>()

    fun startEdit(ev: TEvent) {
        editing = ev; replyTo = null
        val t = ev.plainText
        text = TextFieldValue(t, TextRange(t.length))
    }

    fun clear() { text = TextFieldValue(""); replyTo = null; editing = null; mentions.clear() }

    /** The text to send, with mentions turned into Markdown links. */
    fun markdown(): Pair<String, List<String>> {
        var t = text.text.trim()
        val ids = mutableListOf<String>()
        mentions.distinct().forEach { (name, id) ->
            val token = "@$name"
            if (t.contains(token)) {
                t = t.replace(token, "[$name](https://matrix.to/#/$id)")
                ids += id
            }
        }
        return t to ids
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Composer(
    state: ComposerState,
    hint: String,
    members: List<RoomMember>,
    onSend: () -> Unit,
    onSendLater: () -> Unit,
    onTyping: (Boolean) -> Unit,
    onPickedFiles: (List<Uri>) -> Unit,
    onVoice: (File, Long, List<Float>, String) -> Unit,
) {
    val c = Sbt.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachMenu by remember { mutableStateOf(false) }
    var emojiPicker by remember { mutableStateOf(false) }
    val sendOnEnter by Prefs.sendOnEnter.collectAsState()
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPickedFiles(uris)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPickedFiles(uris)
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) onPickedFiles(listOf(FileProvider.getUriForFile(context, "${context.packageName}.files", f)))
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(recording) {
        while (recording) { elapsed = System.currentTimeMillis() - recordStart; delay(200) }
    }

    // Mention suggestions while typing @name
    val query = remember(state.text) { mentionQuery(state.text) }
    val suggestions = remember(query, members) {
        if (query == null) emptyList() else members.filter {
            val n = (it.displayName ?: it.userId).lowercase()
            n.contains(query.lowercase()) || it.userId.lowercase().contains(query.lowercase())
        }.take(6)
    }

    Column(Modifier.fillMaxWidth().background(c.background)) {
        if (suggestions.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 240.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                items(suggestions, key = { it.userId }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { insertMention(state, m) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m.avatarUrl, m.displayName ?: m.userId, size = 30.dp, key = m.userId)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(m.displayName ?: m.userId, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 14.sp)
                            Text(m.userId, style = Sbt.tiny, color = c.textFaint)
                        }
                    }
                }
            }
        }
        val banner = state.editing ?: state.replyTo
        if (banner != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.editing != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.editing != null) "Editing" else "Replying to ${banner.senderName}", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.accent)
                    Text(banner.plainText, style = Sbt.small, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable {
                    if (state.editing != null) state.text = TextFieldValue("")
                    state.editing = null; state.replyTo = null
                }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Cancel", tint = c.textMuted) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp))
                .background(c.surface).border(1.dp, c.line, RoundedCornerShape(28.dp)).padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { recorder.cancel(); recording = false }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, "Discard", tint = c.danger)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.danger))
                Spacer(Modifier.width(8.dp))
                Text("Recording  ${duration(elapsed)}", style = Sbt.body, color = c.text, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.accent).clickable {
                        val r = recorder.stop()
                        recording = false
                        if (r != null && r.durationMs > 700) onVoice(r.file, r.durationMs, r.waveform, r.mime)
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send voice message", tint = c.onAccent, modifier = Modifier.size(20.dp)) }
                return@Row
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { attachMenu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Attach", tint = c.textMuted)
            }
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (state.text.text.isEmpty()) Text(hint, style = Sbt.body.copy(fontSize = 16.sp), color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = state.text,
                    onValueChange = {
                        val wasEmpty = state.text.text.isEmpty()
                        state.text = it
                        if (wasEmpty != it.text.isEmpty() || it.text.isNotEmpty()) onTyping(it.text.isNotEmpty())
                    },
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (state.text.text.isNotBlank()) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { emojiPicker = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EmojiEmotions, "Emoji", tint = c.textMuted)
            }
            val canSend = state.text.text.isNotBlank()
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.accent)
                    .then(
                        if (canSend) Modifier.combinedClickable(onClick = onSend, onLongClick = { if (state.editing == null) onSendLater() })
                        else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    waitForUpOrCancellation()
                                    return@awaitEachGesture
                                }
                                if (recorder.start()) { recording = true; recordStart = System.currentTimeMillis() }
                                waitForUpOrCancellation()
                                // Holding records; a quick tap leaves the recorder running with send/discard buttons.
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, if (canSend) "Send (hold to send later)" else "Record a voice message", tint = c.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (attachMenu) {
        ActionSheet(
            title = null,
            actions = listOf(
                SheetAction("Photos and videos", Icons.Outlined.PhotoLibrary) {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                SheetAction("Camera", Icons.Outlined.PhotoCamera) {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    cameraFile = f
                    takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
                },
                SheetAction("File", Icons.Outlined.AttachFile) { pickFile.launch(arrayOf("*/*")) },
            ),
            onDismiss = { attachMenu = false },
        )
    }
    if (emojiPicker) {
        EmojiPickerSheet(onDismiss = { emojiPicker = false }) { emoji ->
            val t = state.text
            val sel = t.selection
            val newText = t.text.substring(0, sel.start) + emoji + t.text.substring(sel.end)
            state.text = TextFieldValue(newText, TextRange(sel.start + emoji.length))
        }
    }
}

private fun mentionQuery(v: TextFieldValue): String? {
    val before = v.text.substring(0, v.selection.start.coerceIn(0, v.text.length))
    val at = before.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !before[at - 1].isWhitespace()) return null
    val q = before.substring(at + 1)
    if (q.contains(' ') || q.length > 30) return null
    return q
}

private fun insertMention(state: ComposerState, m: RoomMember) {
    val v = state.text
    val cursor = v.selection.start
    val before = v.text.substring(0, cursor)
    val at = before.lastIndexOf('@')
    val name = m.displayName ?: m.userId.removePrefix("@").substringBefore(':')
    val newText = v.text.substring(0, at) + "@$name " + v.text.substring(cursor)
    state.text = TextFieldValue(newText, TextRange(at + name.length + 2))
    state.mentions += name to m.userId
}

/** Shown instead of the message box where the user can't or doesn't want to post. */
@Composable
fun ReadOnlyNote(canPost: Boolean, onAllow: () -> Unit) {
    val c = Sbt.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (canPost) "Read-only" else "You can't post in this chat", style = Sbt.small, color = c.textMuted, modifier = Modifier.weight(1f))
        if (canPost) TextButton(onClick = onAllow) { Text("Allow posting", color = c.accent, fontFamily = Inter, fontWeight = FontWeight.Bold) }
    }
}

/** Records voice messages as Ogg Opus (or AAC on older phones). */
class VoiceRecorder(private val context: Context) {
    data class Result(val file: File, val durationMs: Long, val waveform: List<Float>, val mime: String)

    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    private var mime = "audio/ogg"
    private val levels = mutableListOf<Float>()
    private var sampler: Thread? = null

    fun start(): Boolean = try {
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val useOpus = Build.VERSION.SDK_INT >= 29
        val f = File(dir, "voice_${System.currentTimeMillis()}." + if (useOpus) "ogg" else "m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (useOpus) {
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG); r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS); mime = "audio/ogg"
        } else {
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mime = "audio/mp4"
        }
        r.setAudioSamplingRate(48000)
        r.setAudioEncodingBitRate(48000)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; file = f; started = System.currentTimeMillis()
        levels.clear()
        sampler = Thread {
            try {
                while (rec != null) {
                    val amp = runCatching { rec?.maxAmplitude ?: 0 }.getOrDefault(0)
                    synchronized(levels) { levels += (amp / 32767f).coerceIn(0f, 1f) }
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) {}
        }.also { it.start() }
        true
    } catch (e: Exception) {
        rec = null; false
    }

    fun stop(): Result? {
        val r = rec ?: return null
        rec = null
        sampler?.interrupt()
        return try {
            r.stop(); r.release()
            val f = file ?: return null
            val wf = synchronized(levels) { levels.toList() }
            Result(f, System.currentTimeMillis() - started, wf.ifEmpty { listOf(0.2f) }, mime)
        } catch (_: Exception) {
            runCatching { r.release() }; null
        }
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        sampler?.interrupt()
        runCatching { r.stop() }; runCatching { r.release() }
        file?.delete()
    }
}
