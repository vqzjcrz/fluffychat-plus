package app.switchboard.push

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.util.Log
import app.switchboard.core.MatrixManager
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.net.HttpURLConnection
import java.net.URL

/**
 * Push notifications through UnifiedPush: a distributor app (ntfy, for
 * example) receives pushes from the homeserver and hands them to this app.
 */
object PushSetup {
    private const val APP_ID = "app.switchboard"
    private const val KEY_ENDPOINT = "push_endpoint"
    private const val KEY_GATEWAY = "push_gateway"
    private const val FALLBACK_GATEWAY = "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify"

    data class Status(val distributor: String?, val endpoint: String?, val gateway: String?, val error: String? = null)

    private val _status = MutableStateFlow(Status(null, null, null))
    val status: StateFlow<Status> = _status

    fun refreshStatus(context: Context) {
        _status.value = Status(UnifiedPush.getAckDistributor(context), Prefs.getString(KEY_ENDPOINT), Prefs.getString(KEY_GATEWAY), _status.value.error)
    }

    fun distributors(context: Context): List<String> = UnifiedPush.getDistributors(context)

    /** Registers with the saved distributor, or the only one installed. */
    fun ensureRegistered(context: Context) {
        val saved = UnifiedPush.getAckDistributor(context)
        if (saved != null) { UnifiedPush.register(context); refreshStatus(context); return }
        val all = UnifiedPush.getDistributors(context)
        if (all.size == 1) {
            UnifiedPush.saveDistributor(context, all[0])
            UnifiedPush.register(context)
        }
        refreshStatus(context)
    }

    /** Lets the user pick the system default distributor (needs an Activity). */
    fun useDefault(activity: Activity, onDone: (Boolean) -> Unit) {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { ok ->
            if (ok) UnifiedPush.register(activity)
            refreshStatus(activity)
            onDone(ok)
        }
    }

    fun choose(context: Context, distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.register(context)
        refreshStatus(context)
    }

    suspend fun unregister(context: Context) {
        val endpoint = Prefs.getString(KEY_ENDPOINT)
        if (endpoint != null) runCatching { Sb.matrix.client?.deletePusher(PusherIdentifiers(endpoint, APP_ID)) }
        runCatching { UnifiedPush.unregister(context) }
        Prefs.putString(KEY_ENDPOINT, null)
        Prefs.putString(KEY_GATEWAY, null)
        refreshStatus(context)
    }

    /** Finds the Matrix gateway for an endpoint: the distributor's own if it has one (ntfy does). */
    private suspend fun gatewayFor(endpoint: String): String = withContext(Dispatchers.IO) {
        try {
            val u = URL(endpoint)
            val candidate = "${u.protocol}://${u.authority}/_matrix/push/v1/notify"
            val conn = URL(candidate).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val ok = conn.responseCode == 200 && conn.inputStream.bufferedReader().use { it.readText() }.let {
                JSONObject(it).optJSONObject("unifiedpush")?.optString("gateway") == "matrix"
            }
            conn.disconnect()
            if (ok) candidate else FALLBACK_GATEWAY
        } catch (_: Exception) {
            FALLBACK_GATEWAY
        }
    }

    suspend fun onNewEndpoint(context: Context, endpoint: String) {
        val client = waitForClient() ?: return
        val old = Prefs.getString(KEY_ENDPOINT)
        val gateway = gatewayFor(endpoint)
        try {
            if (old != null && old != endpoint) runCatching { client.deletePusher(PusherIdentifiers(old, APP_ID)) }
            client.setPusher(
                identifiers = PusherIdentifiers(endpoint, APP_ID),
                kind = PusherKind.Http(HttpPusherData(gateway, PushFormat.EVENT_ID_ONLY, null)),
                appDisplayName = "Switchboard",
                deviceDisplayName = android.os.Build.MODEL ?: "Android",
                profileTag = null,
                lang = java.util.Locale.getDefault().language,
                append = false,
            )
            Prefs.putString(KEY_ENDPOINT, endpoint)
            Prefs.putString(KEY_GATEWAY, gateway)
            _status.value = Status(UnifiedPush.getAckDistributor(context), endpoint, gateway, null)
        } catch (e: Exception) {
            Log.e(MatrixManager.TAG, "setPusher", e)
            _status.value = _status.value.copy(error = e.message)
        }
    }

    suspend fun waitForClient(): org.matrix.rustcomponents.sdk.Client? =
        withTimeoutOrNull(15_000) {
            while (Sb.matrix.client == null) delay(100)
            Sb.matrix.client
        }

    /** Handles one push from the homeserver: fetches the event and shows it. */
    suspend fun onPush(context: Context, raw: String) {
        val n = try { JSONObject(raw).optJSONObject("notification") ?: JSONObject(raw) } catch (_: Exception) { return }
        val roomId = n.optString("room_id").ifEmpty { null }
        val eventId = n.optString("event_id").ifEmpty { null }
        val unread = n.optJSONObject("counts")?.optInt("unread", -1) ?: -1
        if (roomId == null || eventId == null) {
            // A count update: everything was read somewhere else.
            if (unread == 0) Notifier.cancelAll(context)
            return
        }
        if (Snooze.isSnoozed(roomId)) return
        val client = waitForClient() ?: return
        val setup = Sb.matrix.syncService?.let { NotificationProcessSetup.SingleProcess(it) } ?: NotificationProcessSetup.MultipleProcesses
        val nc = client.notificationClient(setup)
        when (val st = nc.getNotification(roomId, eventId)) {
            is NotificationStatus.Event -> if (!QuietReels.applies(roomId, st.item.rawEvent)) Notifier.show(context, st.item, roomId, eventId)
            else -> {}
        }
        app.switchboard.widget.ChatWidget.updateAll(context)
    }
}

/**
 * Reels notify only in the Reels room: a reel (or Instagram post link) in an Instagram chat
 * stays quiet. The Reels room's copies come from the reels hub's bot, not an Instagram
 * chat, so they still notify. Regular messages in Instagram chats notify as usual.
 */
object QuietReels {
    private val link = Regex("instagram\\.com/(?:[^/?#\\s]+/)?(?:reels?|p|tv)/", RegexOption.IGNORE_CASE)

    /** When each person last sent a reel in each Instagram chat. */
    private val lastReel = HashMap<String, Long>()
    private const val ATTACHED_MS = 10_000L

    fun applies(roomId: String, rawEvent: String): Boolean {
        val raw = runCatching { JSONObject(rawEvent) }.getOrNull() ?: return false
        val content = raw.optJSONObject("content")?.let { it.optJSONObject("m.new_content") ?: it } ?: return false
        val sender = raw.optString("sender")
        val ts = raw.optLong("origin_server_ts", System.currentTimeMillis())
        val protocol = app.switchboard.core.Bridges.get(roomId)?.protocol.orEmpty()
        val instagram = protocol.contains("instagram", true) || protocol.contains("meta", true) ||
            app.switchboard.core.Bridges.guess(listOf(sender)) == "Instagram"
        if (!instagram) return false
        val key = "$roomId|$sender"
        val isReel = link.containsMatchIn(content.optString("external_url")) || link.containsMatchIn(content.optString("body"))
        if (isReel) {
            synchronized(lastReel) { lastReel[key] = ts }
            return true
        }
        // A message sent along with a reel is posted with it in the Reels room.
        val reelAt = synchronized(lastReel) { lastReel[key] } ?: return false
        return ts - reelAt in 0..ATTACHED_MS
    }
}

/** Snooze check that works before account data has loaded. */
object Snooze {
    fun isSnoozed(roomId: String): Boolean {
        val until = Sb.matrix.accountData.plus.snoozedUntil(roomId)
        return until != null && until > System.currentTimeMillis()
    }
}

class SbPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val ctx = applicationContext
        Sb.matrix.scope.launch { PushSetup.onNewEndpoint(ctx, endpoint.url) }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val ctx = applicationContext
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "switchboard:push").apply { acquire(30_000) }
        val text = String(message.content)
        Sb.matrix.scope.launch {
            try { PushSetup.onPush(ctx, text) } catch (e: Exception) { Log.w(MatrixManager.TAG, "push", e) } finally { runCatching { wl.release() } }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(MatrixManager.TAG, "UnifiedPush registration failed: $reason")
        PushSetup.refreshStatus(applicationContext)
    }

    override fun onUnregistered(instance: String) {
        Prefs.putString("push_endpoint", null)
        PushSetup.refreshStatus(applicationContext)
    }
}
