package app.switchboard.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.toArgb
import app.switchboard.ui.common.colorFor
import app.switchboard.ui.common.initialOf
import app.switchboard.MainActivity
import app.switchboard.R
import app.switchboard.core.Mxc
import app.switchboard.core.Previews
import app.switchboard.core.Sb
import app.switchboard.core.Sending
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.TimelineEventContent

object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_QUIET = "messages_quiet"
    const val CH_INVITES = "invites"
    private const val GROUP = "switchboard_messages"
    const val KEY_REPLY = "reply_text"
    const val ACTION_REPLY = "app.switchboard.REPLY"
    const val ACTION_READ = "app.switchboard.MARK_READ"

    /** The chat on screen right now; its messages don't notify. */
    @Volatile var activeRoom: String? = null

    private data class Line(val sender: String, val senderId: String, val text: String, val ts: Long, val icon: IconCompat?)
    private const val PIC = 128
    private val history = HashMap<String, MutableList<Line>>()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages and mentions"
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUIET, "Quiet messages", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Messages your push rules mark as silent"
        })
        nm.createNotificationChannel(NotificationChannel(CH_INVITES, "Invites", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canPost(context: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    suspend fun show(context: Context, item: NotificationItem, roomId: String, eventId: String) {
        if (!canPost(context) || roomId == activeRoom) return
        val roomName = Sb.matrix.accountData.plus.nickname(roomId) ?: item.roomInfo.displayName
        val senderName = item.senderInfo.displayName ?: "Someone"
        val open = PendingIntent.getActivity(
            context, roomId.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROOM_ID, roomId).putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when (val ev = item.event) {
            is NotificationEvent.Invite -> {
                val pic = withTimeoutOrNull(3000) { picture(roomAvatarUrl(roomId, item), roomName, roomId) } ?: letter(roomName, roomId)
                val n = NotificationCompat.Builder(context, CH_INVITES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(pic)
                    .setContentTitle(roomName)
                    .setContentText("$senderName invited you")
                    .setContentIntent(open).setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .build()
                NotificationManagerCompat.from(context).notify(roomId.hashCode(), n)
            }
            is NotificationEvent.Timeline -> {
                val content = ev.event.content()
                val text = (content as? TimelineEventContent.MessageLike)?.content?.let { Previews.notificationBody(it) } ?: return
                val senderId = ev.event.senderId()
                val senderPic = withTimeoutOrNull(3000) { picture(item.senderInfo.avatarUrl, senderName, senderId) } ?: letter(senderName, senderId)
                val icon = IconCompat.createWithBitmap(senderPic)
                // The chat's own picture (or its space's) stands in for the app icon.
                val chatPic = if (item.roomInfo.isDirect) null
                    else withTimeoutOrNull(3000) { picture(roomAvatarUrl(roomId, item), roomName, roomId) } ?: letter(roomName, roomId)
                val chatIcon = chatPic?.let { IconCompat.createWithBitmap(it) } ?: icon
                val shortcut = publishShortcut(
                    context, roomId, if (item.roomInfo.isDirect) senderName else roomName, chatIcon,
                    if (item.roomInfo.isDirect) Person.Builder().setName(senderName).setKey(senderId).setIcon(icon).build() else null,
                )
                val lines = synchronized(history) {
                    history.getOrPut(roomId) { mutableListOf() }.also {
                        it += Line(senderName, senderId, text, ev.event.timestamp().toLong(), icon)
                        while (it.size > 8) it.removeAt(0)
                    }.toList()
                }
                val me = Person.Builder().setName("You").setKey(Sb.matrix.userId ?: "me").build()
                val style = NotificationCompat.MessagingStyle(me)
                    .setGroupConversation(!item.roomInfo.isDirect)
                    .setConversationTitle(if (item.roomInfo.isDirect) null else roomName)
                lines.forEach { l ->
                    style.addMessage(l.text, l.ts, Person.Builder().setName(l.sender).setKey(l.senderId).setIcon(l.icon).build())
                }
                // Everything your server pushes alerts, like FluffyChat did. The server only marks
                // one-to-one rooms as "noisy", and bridged DMs have the bridge bot as a third
                // member, so they'd otherwise land silently in the shade. Mute or set a chat to
                // mentions-only to quiet it.
                val noisy = true
                val reply = NotificationCompat.Action.Builder(
                    R.drawable.ic_notification, "Reply",
                    PendingIntent.getBroadcast(
                        context, roomId.hashCode() + 1,
                        Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_REPLY).putExtra(MainActivity.EXTRA_ROOM_ID, roomId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(true).build()
                val read = NotificationCompat.Action.Builder(
                    R.drawable.ic_notification, "Mark as read",
                    PendingIntent.getBroadcast(
                        context, roomId.hashCode() + 2,
                        Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_READ).putExtra(MainActivity.EXTRA_ROOM_ID, roomId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()
                val n = NotificationCompat.Builder(context, if (noisy) CH_MESSAGES else CH_QUIET)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(style)
                    .apply {
                        if (shortcut) setShortcutId(roomId).setLocusId(LocusIdCompat(roomId))
                        if (android.os.Build.VERSION.SDK_INT < 30 || !shortcut) setLargeIcon(chatPic ?: senderPic)
                    }
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(GROUP)
                    .setOnlyAlertOnce(false)
                    .setSilent(!noisy)
                    .addAction(reply)
                    .addAction(read)
                    .setWhen(ev.event.timestamp().toLong())
                    .build()
                NotificationManagerCompat.from(context).notify(roomId.hashCode(), n)
                val summary = NotificationCompat.Builder(context, CH_MESSAGES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setGroup(GROUP).setGroupSummary(true).setSilent(true).setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(GROUP.hashCode(), summary)
            }
        }
    }

    /** A chat's picture: its own, else its space's (like a Discord server icon). */
    private fun roomAvatarUrl(roomId: String, item: NotificationItem): String? =
        item.roomInfo.avatarUrl ?: Sb.matrix.summary(roomId)?.avatarUrl
            ?: Sb.matrix.spaceChildren.value.entries.firstOrNull { (_, kids) -> kids.any { it.roomId == roomId } }
                ?.key?.let { Sb.matrix.summary(it)?.avatarUrl }

    /** Round picture from an mxc:// avatar, or null if there's none or it can't load. */
    private suspend fun picture(url: String?, name: String, key: String): Bitmap? {
        val mxc = Mxc.avatar(url, PIC) ?: return null
        val bytes = runCatching { Sb.matrix.client?.getMediaThumbnail(mxc.source(), PIC.toULong(), PIC.toULong()) }.getOrNull() ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return circle(bmp)
    }

    /** Initial on a colored circle, matching the avatars in the app. */
    private fun letter(name: String, key: String): Bitmap {
        val out = Bitmap.createBitmap(PIC, PIC, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawCircle(PIC / 2f, PIC / 2f, PIC / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(key).toArgb() })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = PIC * 0.42f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val y = PIC / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initialOf(name), PIC / 2f, y, text)
        return out
    }

    /**
     * Publishes the chat as a conversation shortcut. Android then shows the chat's picture
     * on its notifications instead of the app icon, and lists it under Conversations.
     */
    private fun publishShortcut(context: Context, roomId: String, name: String, icon: IconCompat, person: Person?): Boolean = runCatching {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
        val label = name.ifBlank { "Chat" }
        val info = ShortcutInfoCompat.Builder(context, roomId)
            .setShortLabel(label.take(25))
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(roomId))
            .apply { if (person != null) setPerson(person) }
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, info)
        true
    }.getOrDefault(false)

    private fun circle(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(src, ((size - src.width) / 2f), ((size - src.height) / 2f), Paint(Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun cancel(context: Context, roomId: String) {
        synchronized(history) { history.remove(roomId) }
        NotificationManagerCompat.from(context).cancel(roomId.hashCode())
    }

    fun cancelAll(context: Context) {
        synchronized(history) { history.clear() }
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** Adds the user's own reply to the notification so it doesn't look unanswered. */
    fun addOwnReply(context: Context, roomId: String, text: String) {
        synchronized(history) { history[roomId]?.add(Line("You", Sb.matrix.userId ?: "me", text, System.currentTimeMillis(), null)) }
        cancel(context, roomId)
    }
}

/** Handles Reply and Mark as read from notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(MainActivity.EXTRA_ROOM_ID) ?: return
        val pending = goAsync()
        Sb.matrix.scope.launch {
            try {
                PushSetup.waitForClient() ?: return@launch
                val room = Sb.matrix.room(roomId) ?: Sb.matrix.client?.getRoom(roomId) ?: return@launch
                when (intent.action) {
                    Notifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            val t = room.timeline()
                            app.switchboard.core.Receipts.beforeSending(t)
                            t.send(Sending.content(text, emptyList()))
                            Notifier.addOwnReply(context, roomId, text)
                        }
                    }
                    Notifier.ACTION_READ -> {
                        runCatching { room.markAsRead(app.switchboard.core.Receipts.readType) }
                        Notifier.cancel(context, roomId)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
