package app.switchboard.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.AudioInfo
import org.matrix.rustcomponents.sdk.DelayedEventUpdateAction
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.VideoInfo
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdownAsEmote
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.util.UUID

/** A file picked from another app, copied into the cache so it can be uploaded. */
data class LocalFile(val file: File, val name: String, val mime: String, val size: Long)

object Sending {
    suspend fun copyToCache(context: Context, uri: Uri): LocalFile = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = cr.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        val out = File(dir, UUID.randomUUID().toString().take(8) + "_" + name.replace('/', '_'))
        cr.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            ?: throw IllegalStateException("Couldn't read the file")
        LocalFile(out, name, mime, if (size > 0) size else out.length())
    }

    private fun params(f: File, replyTo: String?, caption: String? = null) =
        UploadParameters(UploadSource.File(f.absolutePath), caption, null, null, replyTo)

    /** Sends a picked file as the right kind of message. */
    suspend fun sendFile(context: Context, timeline: Timeline, lf: LocalFile, replyTo: String?, caption: String? = null) = withContext(Dispatchers.IO) {
        when {
            lf.mime.startsWith("image/") && lf.mime != "image/svg+xml" -> {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(lf.file.absolutePath, o)
                val info = ImageInfo(
                    height = o.outHeight.takeIf { it > 0 }?.toULong(), width = o.outWidth.takeIf { it > 0 }?.toULong(),
                    mimetype = lf.mime, size = lf.size.toULong(), thumbnailInfo = null, thumbnailSource = null,
                    blurhash = null, isAnimated = lf.mime == "image/gif",
                )
                timeline.sendImage(params(lf.file, replyTo, caption), null, info).join()
            }
            lf.mime.startsWith("video/") -> {
                val r = MediaMetadataRetriever()
                var w = 0; var h = 0; var dur = 0L
                var thumb: File? = null
                try {
                    r.setDataSource(lf.file.absolutePath)
                    w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                    dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    r.getFrameAtTime(0)?.let { bmp ->
                        val tf = File(lf.file.parentFile, lf.file.name + ".thumb.jpg")
                        FileOutputStream(tf).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                        thumb = tf
                    }
                } catch (_: Exception) {
                } finally { runCatching { r.release() } }
                val info = VideoInfo(
                    duration = Duration.ofMillis(dur), height = h.toULong(), width = w.toULong(), mimetype = lf.mime,
                    size = lf.size.toULong(), thumbnailInfo = null, thumbnailSource = null, blurhash = null,
                )
                timeline.sendVideo(params(lf.file, replyTo, caption), thumb?.let { UploadSource.File(it.absolutePath) }, info).join()
            }
            lf.mime.startsWith("audio/") -> {
                val dur = runCatching {
                    MediaMetadataRetriever().run {
                        setDataSource(lf.file.absolutePath)
                        val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                        release(); d
                    }
                }.getOrDefault(0L)
                timeline.sendAudio(params(lf.file, replyTo, caption), AudioInfo(Duration.ofMillis(dur), lf.size.toULong(), lf.mime)).join()
            }
            else -> timeline.sendFile(params(lf.file, replyTo, caption), FileInfo(lf.mime, lf.size.toULong(), null, null)).join()
        }
    }

    suspend fun sendVoice(timeline: Timeline, file: File, durationMs: Long, waveform: List<Float>, mime: String, replyTo: String?) =
        withContext(Dispatchers.IO) {
            timeline.sendVoiceMessage(
                UploadParameters(UploadSource.File(file.absolutePath), null, null, null, replyTo),
                AudioInfo(Duration.ofMillis(durationMs), file.length().toULong(), mime),
                waveform,
            ).join()
        }

    /** Builds the message content from what was typed (Markdown, /me). */
    fun content(text: String, mentions: List<String>) = run {
        val emote = text.startsWith("/me ")
        val c = if (emote) messageEventContentFromMarkdownAsEmote(text.removePrefix("/me ")) else messageEventContentFromMarkdown(text)
        c.withMentions(Mentions(mentions, text.contains("@room")))
    }
}

/** A message handed to the homeserver to send later (MSC4140 delayed events). */
data class Scheduled(val delayId: String, val roomId: String, val body: String, val sendAt: Long)

object ScheduledMessages {
    private const val KEY = "scheduled_messages"
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Scheduled>> = _all

    private fun load(): List<Scheduled> {
        val a = Prefs.jsonArray(KEY)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Scheduled(o.optString("delay_id"), o.optString("room_id"), o.optString("body"), o.optLong("send_at"))
        }
    }

    private fun save(list: List<Scheduled>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("delay_id", it.delayId).put("room_id", it.roomId).put("body", it.body).put("send_at", it.sendAt)) }
        Prefs.putJsonArray(KEY, a)
        _all.value = list
    }

    fun forRoom(roomId: String, list: List<Scheduled> = _all.value): List<Scheduled> {
        val cutoff = System.currentTimeMillis() - 60_000
        return list.filter { it.roomId == roomId && it.sendAt > cutoff }.sortedBy { it.sendAt }
    }

    suspend fun supported(): Boolean = Api.unstableFeatures().optBoolean("org.matrix.msc4140")

    suspend fun schedule(room: Room, text: String, sendAt: Long) {
        val delay = (sendAt - System.currentTimeMillis()).coerceAtLeast(1000)
        val content = JSONObject().put("msgtype", "m.text").put("body", text)
        val id = room.sendDelayedEvent("m.room.message", content.toString(), delay.toULong(), null)
        save(_all.value + Scheduled(id, room.id(), text, sendAt))
    }

    suspend fun sendNow(s: Scheduled) {
        Sb.matrix.client?.updateDelayedEvent(s.delayId, DelayedEventUpdateAction.SEND, null)
        save(_all.value.filter { it.delayId != s.delayId })
    }

    suspend fun cancel(s: Scheduled) {
        runCatching { Sb.matrix.client?.updateDelayedEvent(s.delayId, DelayedEventUpdateAction.CANCEL, null) }
        save(_all.value.filter { it.delayId != s.delayId })
    }
}
