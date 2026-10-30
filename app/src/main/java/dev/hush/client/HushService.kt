package dev.hush.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dev.hush.client.discord.NotifyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.abs

/**
 * Keeps the Discord gateway connected while the app is in the background and turns
 * incoming DMs / mentions into system notifications. Started while signed in with
 * notifications enabled; stopped on logout or when the setting is turned off.
 */
class HushService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null
    private val avatarCache = HashMap<String, Bitmap?>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())

        val repo = (application as HushApp).repository
        repo.serviceActive = true
        repo.start()

        if (collectJob == null) {
            collectJob = scope.launch {
                repo.notifications.collect { ev -> notifyMessage(ev) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        (application as HushApp).repository.serviceActive = false
        super.onDestroy()
    }

    private suspend fun notifyMessage(ev: NotifyEvent) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHANNEL_ID, ev.channelId)
        }
        val pending = PendingIntent.getActivity(
            this,
            ev.channelId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val avatar = avatarBitmap(ev.avatarUrl)
        val person = Person.Builder()
            .setName(ev.title)
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(ev.body, System.currentTimeMillis(), person)

        val builder = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ev.title)
            .setContentText(ev.body)
            .setStyle(style)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
        avatar?.let { builder.setLargeIcon(it) }

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(abs(ev.channelId.hashCode()) % 1_000_000 + 10, builder.build())
        }
    }

    private suspend fun avatarBitmap(url: String): Bitmap? {
        avatarCache[url]?.let { return it }
        if (avatarCache.containsKey(url)) return null
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (avatarCache.size < 64) avatarCache[url] = bmp
        return bmp
    }

    private fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hush")
            .setContentText("Listening for messages")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                0, "Stop",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, HushService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Direct messages and mentions"
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Running", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Hush connected for notifications"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
        private const val ACTION_STOP = "dev.hush.client.STOP_SERVICE"
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_SERVICE = "service"
        private const val SERVICE_NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, HushService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HushService::class.java))
        }
    }
}
