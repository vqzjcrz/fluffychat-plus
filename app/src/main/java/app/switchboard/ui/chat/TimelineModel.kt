package app.switchboard.ui.chat

import android.util.Log
import androidx.compose.runtime.Immutable
import app.switchboard.core.MatrixManager
import app.switchboard.core.Previews
import app.switchboard.core.Sb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.EmbeddedEventDetails
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.OtherState
import org.matrix.rustcomponents.sdk.PaginationStatusListener
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.VirtualTimelineItem
import uniffi.matrix_sdk.PaginationStatus

/** What a message shows. */
sealed interface Body {
    data class Text(val text: String, val html: String?, val emote: Boolean = false, val notice: Boolean = false, val onlyEmoji: Boolean = false) : Body
    data class Image(val source: MediaSource, val thumb: MediaSource?, val w: Int, val h: Int, val caption: String?, val filename: String, val mime: String?, val animated: Boolean) : Body
    data class Video(val source: MediaSource, val thumb: MediaSource?, val w: Int, val h: Int, val durationMs: Long, val caption: String?, val filename: String, val mime: String?) : Body
    data class Reel(val source: MediaSource, val thumb: MediaSource?, val url: String, val author: String, val origin: String?, val caption: String, val mime: String?, val filename: String) : Body
    data class Audio(val source: MediaSource, val durationMs: Long, val voice: Boolean, val waveform: List<Float>, val filename: String, val mime: String?) : Body
    data class File(val source: MediaSource, val filename: String, val size: Long, val mime: String?, val caption: String?) : Body
    data class Sticker(val source: MediaSource, val w: Int, val h: Int, val body: String) : Body
    data class Location(val body: String, val geoUri: String) : Body
    data class Poll(val question: String, val answers: List<Pair<String, String>>, val votes: Map<String, List<String>>, val ended: Boolean) : Body
    data object Redacted : Body
    data object Encrypted : Body
    /** Membership changes, name changes and other room updates. */
    data class Notice(val text: String) : Body
}

data class ReplyPreview(val eventId: String, val sender: String, val text: String)
data class ReactionGroup(val key: String, val count: Int, val mine: Boolean, val senders: List<String>)

@Immutable
sealed interface TItem { val key: String }

data class TDate(override val key: String, val ts: Long) : TItem
data class TReadMarker(override val key: String) : TItem
data class TStart(override val key: String) : TItem
data class TEvent(
    override val key: String,
    val item: EventTimelineItem,
    val eventId: String?,
    val id: EventOrTransactionId,
    val sender: String,
    val senderName: String,
    val senderAvatar: String?,
    val isOwn: Boolean,
    val ts: Long,
    val body: Body,
    val reactions: List<ReactionGroup>,
    val reply: ReplyPreview?,
    val edited: Boolean,
    val sending: Boolean,
    val failed: Boolean,
    val readBy: List<String>,
    val canReply: Boolean,
    val canEdit: Boolean,
    val threadReplies: Int,
) : TItem {
    val isMessage get() = body !is Body.Notice
    /** Plain text for copying, quoting and previews. */
    val plainText: String get() = when (val b = body) {
        is Body.Text -> b.text
        is Body.Image -> b.caption ?: "Photo"
        is Body.Video -> b.caption ?: "Video"
        is Body.Reel -> b.url
        is Body.Audio -> if (b.voice) "Voice message" else "Audio"
        is Body.File -> b.filename
        is Body.Sticker -> b.body
        is Body.Location -> b.body
        is Body.Poll -> b.question
        Body.Redacted -> "Message deleted"
        Body.Encrypted -> "Encrypted message"
        is Body.Notice -> b.text
    }
}

private val reelUrl = Regex("instagram\\.com/(?:[^/?#]+/)?(reels?|p)/", RegexOption.IGNORE_CASE)
private val hubHeader = Regex("^\\x{1F39E}\\x{FE0F}?\\s*")
private val emojiOnly = Regex("^[\\p{So}\\p{Sk}\\p{Cs}\\x{200D}\\x{FE0F}\\x{20E3}\\s]{1,24}$")

object TimelineMapper {
    fun map(raw: TimelineItem, ownUserId: String): TItem? {
        val key = raw.uniqueId().id
        raw.asVirtual()?.let { v ->
            return when (v) {
                is VirtualTimelineItem.DateDivider -> TDate(key, v.ts.toLong())
                is VirtualTimelineItem.ReadMarker -> TReadMarker(key)
                is VirtualTimelineItem.TimelineStart -> TStart(key)
            }
        }
        val e = raw.asEvent() ?: return null
        return mapEvent(key, e, ownUserId)
    }

    fun mapEvent(key: String, e: EventTimelineItem, ownUserId: String): TEvent? {
        val name = Previews.name(e.senderProfile, e.sender)
        val avatar = (e.senderProfile as? ProfileDetails.Ready)?.avatarUrl
        var reply: ReplyPreview? = null
        var edited = false
        var threadReplies = 0
        val body: Body = when (val c = e.content) {
            is TimelineItemContent.MsgLike -> {
                val ml = c.content
                ml.inReplyTo?.let { r ->
                    val details = r.event()
                    reply = if (details is EmbeddedEventDetails.Ready) {
                        ReplyPreview(r.eventId(), Previews.name(details.senderProfile, details.sender), Previews.content(details.content))
                    } else ReplyPreview(r.eventId(), "", "Loading…")
                }
                threadReplies = runCatching { ml.threadSummary?.numReplies()?.toInt() ?: 0 }.getOrDefault(0)
                when (val k = ml.kind) {
                    is MsgLikeKind.Message -> {
                        edited = k.content.isEdited
                        message(k.content.msgType, k.content.body, e, name)
                    }
                    is MsgLikeKind.Sticker -> Body.Sticker(k.source, k.info.width?.toInt() ?: 0, k.info.height?.toInt() ?: 0, k.body)
                    is MsgLikeKind.Poll -> Body.Poll(k.question, k.answers.map { it.id to it.text }, k.votes, k.endTime != null)
                    is MsgLikeKind.Redacted -> Body.Redacted
                    is MsgLikeKind.UnableToDecrypt -> Body.Encrypted
                    else -> return null
                }
            }
            is TimelineItemContent.RoomMembership -> Body.Notice(Previews.membership(c.userDisplayName ?: Previews.localpart(c.userId), c.change))
            is TimelineItemContent.ProfileChange -> Body.Notice(
                if (c.displayName != c.prevDisplayName) "${c.prevDisplayName ?: name} is now ${c.displayName ?: Previews.localpart(e.sender)}"
                else "$name changed their profile picture",
            )
            is TimelineItemContent.State -> stateText(c.content, name) ?: return null
            is TimelineItemContent.CallInvite, is TimelineItemContent.RtcNotification -> Body.Notice("$name started a call")
            else -> return null
        }
        val reactions = e.reactions.map { r ->
            ReactionGroup(r.key, r.senders.size, r.senders.any { it.senderId == ownUserId }, r.senders.map { it.senderId })
        }
        val send = e.localSendState
        val eventId = (e.eventOrTransactionId as? EventOrTransactionId.EventId)?.eventId
        return TEvent(
            key = key, item = e, eventId = eventId, id = e.eventOrTransactionId,
            sender = e.sender, senderName = name, senderAvatar = avatar, isOwn = e.isOwn,
            ts = e.timestamp.toLong(), body = body, reactions = reactions, reply = reply, edited = edited,
            sending = send is EventSendState.NotSentYet, failed = send is EventSendState.SendingFailed,
            readBy = e.readReceipts.keys.filter { it != ownUserId && it != e.sender },
            canReply = e.canBeRepliedTo, canEdit = e.isEditable, threadReplies = threadReplies,
        )
    }

    private fun message(t: MessageType, body: String, e: EventTimelineItem, senderName: String): Body = when (t) {
        is MessageType.Text -> Body.Text(t.content.body, t.content.formatted?.body, onlyEmoji = emojiOnly.matches(t.content.body.trim()))
        is MessageType.Notice -> Body.Text(t.content.body, t.content.formatted?.body, notice = true)
        is MessageType.Emote -> Body.Text(t.content.body, t.content.formatted?.body, emote = true)
        is MessageType.Image -> {
            val i = t.content.info
            Body.Image(t.content.source, i?.thumbnailSource, i?.width?.toInt() ?: 0, i?.height?.toInt() ?: 0,
                t.content.caption, t.content.filename, i?.mimetype, i?.isAnimated == true || i?.mimetype == "image/gif")
        }
        is MessageType.Video -> {
            val i = t.content.info
            reel(e, t, senderName) ?: Body.Video(t.content.source, i?.thumbnailSource, i?.width?.toInt() ?: 0, i?.height?.toInt() ?: 0,
                i?.duration?.toMillis() ?: 0, t.content.caption, t.content.filename, i?.mimetype)
        }
        is MessageType.Audio -> {
            val wf = t.content.audio?.waveform?.map { it.toFloat() / 1024f } ?: emptyList()
            Body.Audio(t.content.source, t.content.info?.duration?.toMillis() ?: t.content.audio?.duration?.toMillis() ?: 0,
                t.content.voice != null, wf, t.content.filename, t.content.info?.mimetype)
        }
        is MessageType.File -> Body.File(t.content.source, t.content.filename, t.content.info?.size?.toLong() ?: 0, t.content.info?.mimetype, t.content.caption)
        is MessageType.Location -> Body.Location(t.content.body, t.content.geoUri)
        is MessageType.Gallery -> Body.Text(t.content.body, t.content.formatted?.body)
        is MessageType.Other -> Body.Text(t.body, null)
    }

    /** A video bridged from Instagram (or reposted by the reels hub) shows as a reel card. */
    private fun reel(e: EventTimelineItem, t: MessageType.Video, senderName: String): Body.Reel? {
        val json = try { e.lazyProvider.latestJson() } catch (_: Exception) { null } ?: return null
        val content = try {
            val o = JSONObject(json)
            o.optJSONObject("content")?.let { c -> c.optJSONObject("m.new_content") ?: c } ?: return null
        } catch (_: Exception) { return null }
        val url = content.optString("external_url")
        if (url.isEmpty() || !reelUrl.containsMatchIn(url)) return null
        val body = content.optString("body")
        val filename = content.optString("filename").ifEmpty { null }
        val hasCaption = body.isNotEmpty() && filename != null && filename != body
        var lines = if (hasCaption) body.split('\n') else emptyList()
        var author = senderName
        var origin: String? = null
        if (lines.isNotEmpty() && hubHeader.containsMatchIn(lines.first())) {
            val header = lines.first().replace(hubHeader, "").trim()
            val sep = header.lastIndexOf(" · ")
            if (sep > 0) { author = header.substring(0, sep); origin = header.substring(sep + 3) } else author = header
            lines = lines.drop(1)
        }
        val caption = lines.filter { it.trim() != url }.joinToString("\n").trim()
        val i = t.content.info
        return Body.Reel(t.content.source, i?.thumbnailSource, url, author, origin?.takeIf { it != "DM" }, caption, i?.mimetype, t.content.filename)
    }

    private fun stateText(s: OtherState, who: String): Body? = when (s) {
        is OtherState.RoomName -> Body.Notice("$who renamed the chat to ${s.name ?: "nothing"}")
        is OtherState.RoomTopic -> Body.Notice("$who changed the topic")
        is OtherState.RoomAvatar -> Body.Notice("$who changed the chat picture")
        is OtherState.RoomCreate -> Body.Notice("$who created the chat")
        is OtherState.RoomEncryption -> Body.Notice("Messages are end-to-end encrypted")
        is OtherState.RoomPinnedEvents -> Body.Notice("$who changed the pinned messages")
        else -> null
    }
}

/** Keeps a room's timeline as a list the chat screen can show. [focus] opens it around an older event. */
class TimelineState(val room: Room, val focus: String? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val raw = ArrayList<TimelineItem>()
    private val diffs = Channel<List<TimelineDiff>>(Channel.UNLIMITED)
    private val _items = MutableStateFlow<List<TItem>>(emptyList())
    /** Oldest first. */
    val items: StateFlow<List<TItem>> = _items
    private val _paginating = MutableStateFlow(false)
    val paginating: StateFlow<Boolean> = _paginating
    private val _atStart = MutableStateFlow(false)
    val atStart: StateFlow<Boolean> = _atStart
    var timeline: Timeline? = null
        private set
    private val handles = mutableListOf<TaskHandle>()
    private val own = Sb.matrix.userId ?: ""

    fun start(onReady: (Timeline) -> Unit = {}) {
        scope.launch {
            try {
                val t = if (focus == null) room.timeline() else room.timelineWithConfiguration(
                    org.matrix.rustcomponents.sdk.TimelineConfiguration(
                        focus = org.matrix.rustcomponents.sdk.TimelineFocus.Event(focus, 25u, uniffi.matrix_sdk_ui.TimelineEventFocusThreadMode.Automatic(false)),
                        filter = org.matrix.rustcomponents.sdk.TimelineFilter.All,
                        internalIdPrefix = "focus",
                        dateDividerMode = org.matrix.rustcomponents.sdk.DateDividerMode.DAILY,
                        trackReadReceipts = uniffi.matrix_sdk_ui.TimelineReadReceiptTracking.MESSAGE_LIKE_EVENTS,
                        reportUtds = false,
                    ),
                )
                timeline = t
                handles += t.addListener(object : TimelineListener {
                    override fun onUpdate(diff: List<TimelineDiff>) { diffs.trySend(diff) }
                })
                handles += t.subscribeToBackPaginationStatus(object : PaginationStatusListener {
                    override fun onUpdate(status: PaginationStatus) {
                        _paginating.value = status is PaginationStatus.Paginating
                        if (status is PaginationStatus.Idle) _atStart.value = status.hitTimelineStart
                    }
                })
                onReady(t)
                if (focus == null && raw.size < 20) paginate()
            } catch (e: Exception) {
                Log.e(MatrixManager.TAG, "timeline", e)
            }
        }
        scope.launch {
            for (batch in diffs) {
                for (d in batch) apply(d)
                _items.value = raw.mapNotNull { runCatching { TimelineMapper.map(it, own) }.getOrNull() }
            }
        }
    }

    private fun apply(d: TimelineDiff) {
        when (d) {
            is TimelineDiff.Append -> raw.addAll(d.values)
            is TimelineDiff.Clear -> raw.clear()
            is TimelineDiff.PushFront -> raw.add(0, d.value)
            is TimelineDiff.PushBack -> raw.add(d.value)
            is TimelineDiff.PopFront -> if (raw.isNotEmpty()) raw.removeAt(0)
            is TimelineDiff.PopBack -> if (raw.isNotEmpty()) raw.removeAt(raw.size - 1)
            is TimelineDiff.Insert -> raw.add(d.index.toInt().coerceIn(0, raw.size), d.value)
            is TimelineDiff.Set -> { val i = d.index.toInt(); if (i in raw.indices) raw[i] = d.value }
            is TimelineDiff.Remove -> { val i = d.index.toInt(); if (i in raw.indices) raw.removeAt(i) }
            is TimelineDiff.Truncate -> { val n = d.length.toInt(); while (raw.size > n) raw.removeAt(raw.size - 1) }
            is TimelineDiff.Reset -> { raw.clear(); raw.addAll(d.values) }
        }
    }

    fun paginate() {
        if (_paginating.value || _atStart.value) return
        val t = timeline ?: return
        scope.launch {
            _paginating.value = true
            try { if (t.paginateBackwards(30u)) _atStart.value = true } catch (e: Exception) { Log.w(MatrixManager.TAG, "paginate", e) }
            _paginating.value = false
        }
    }

    private var forwardBusy = false

    /** Loads newer messages (only for a timeline opened around an older event). */
    fun paginateForward() {
        if (focus == null || forwardBusy) return
        val t = timeline ?: return
        forwardBusy = true
        scope.launch {
            try { t.paginateForwards(30u) } catch (_: Exception) {}
            forwardBusy = false
        }
    }

    fun close() {
        handles.forEach { runCatching { it.cancel() } }
        handles.clear()
        scope.cancel()
    }
}
