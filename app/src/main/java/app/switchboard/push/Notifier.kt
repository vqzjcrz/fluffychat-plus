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
import androidx.core.graphics.drawable.IconCompat
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
                val n = NotificationCompat.Builder(context, CH_INVITES)
                    .setSmallIcon(R.drawable.ic_notification)
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
                val icon = withTimeoutOrNull(3000) { avatarIcon(item.senderInfo.avatarUrl) }
                val lines = synchronized(history) {
                    history.getOrPut(roomId) { mutableListOf() }.also {
                        it += Line(senderName, ev.event.senderId(), text, ev.event.timestamp().toLong(), icon)
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
                val noisy = item.isNoisy ?: true
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

    private suspend fun avatarIcon(url: String?): IconCompat? {
        val mxc = Mxc.avatar(url, 128) ?: return null
        val bytes = runCatching { Sb.matrix.client?.getMediaThumbnail(mxc.source(), 128u, 128u) }.getOrNull() ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return IconCompat.createWithBitmap(circle(bmp))
    }

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
                            room.timeline().send(Sending.content(text, emptyList()))
                            Notifier.addOwnReply(context, roomId, text)
                        }
                    }
                    Notifier.ACTION_READ -> {
                        runCatching { room.markAsRead(ReceiptType.READ) }
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
