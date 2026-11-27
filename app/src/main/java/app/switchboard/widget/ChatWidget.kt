package app.switchboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.MainActivity
import app.switchboard.R
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.ui.chat.TEvent
import app.switchboard.ui.chat.TimelineMapper
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import app.switchboard.ui.theme.SwitchboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineListener

/**
 * Home screen widget showing one chat's recent messages and unread count.
 * Messages are kept only in the app's private storage.
 */
class ChatWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        refresh(context, ids.toList())
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { Prefs.putString(roomKey(it), null) }
    }

    companion object {
        private const val MAX_MESSAGES = 20

        fun roomKey(widgetId: Int) = "widget_room_$widgetId"
        fun dataKey(roomId: String) = "widget_data_$roomId"

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ChatWidget::class.java))

        /** Keeps widgets current while the app is running. */
        fun observe(context: Context) {
            val app = context.applicationContext
            Sb.matrix.scope.launch {
                var last = ""
                Sb.matrix.summaries.collect { map ->
                    val ids = ids(app)
                    if (ids.isEmpty()) return@collect
                    val rooms = ids.toList().mapNotNull { Prefs.getString(roomKey(it)) }
                    val sig = rooms.joinToString { r -> map[r]?.let { "${it.lastTs}/${it.notificationCount}/${it.isUnread}" } ?: "" }
                    if (sig != last) {
                        last = sig
                        delay(1500)
                        refresh(app, ids.toList())
                    }
                }
            }
        }

        /** Re-reads every widget's chat and redraws it. */
        fun updateAll(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            refresh(context, ids.toList())
        }

        private fun refresh(context: Context, ids: List<Int>) {
            val app = context.applicationContext
            Sb.matrix.scope.launch {
                withTimeoutOrNull(15_000) { while (Sb.matrix.client == null) delay(150) } ?: return@launch
                val rooms = ids.mapNotNull { Prefs.getString(roomKey(it)) }.distinct()
                for (roomId in rooms) runCatching { collect(roomId) }
                val manager = AppWidgetManager.getInstance(app)
                ids.forEach { render(app, manager, it) }
                manager.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.widget_messages)
            }
        }

        /** Saves the chat's name, unread count and last messages for the widget. */
        private suspend fun collect(roomId: String) {
            val room = Sb.matrix.room(roomId) ?: return
            val info = runCatching { room.roomInfo() }.getOrNull()
            val t = room.timeline()
            val items = ArrayList<TimelineItem>()
            val h = t.addListener(object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) {
                    synchronized(items) {
                        for (d in diff) when (d) {
                            is TimelineDiff.Reset -> { items.clear(); items.addAll(d.values) }
                            is TimelineDiff.Append -> items.addAll(d.values)
                            is TimelineDiff.PushBack -> items.add(d.value)
                            is TimelineDiff.PushFront -> items.add(0, d.value)
                            is TimelineDiff.Insert -> items.add(d.index.toInt().coerceIn(0, items.size), d.value)
                            is TimelineDiff.Set -> if (d.index.toInt() in items.indices) items[d.index.toInt()] = d.value
                            else -> {}
                        }
                    }
                }
            })
            val own = Sb.matrix.userId ?: ""
            fun messages() = synchronized(items) { items.toList() }
                .mapNotNull { TimelineMapper.map(it, own) as? TEvent }.filter { it.isMessage }
            delay(250)
            repeat(3) {
                if (messages().size >= MAX_MESSAGES) return@repeat
                val done = runCatching { t.paginateBackwards(30u) }.getOrDefault(true)
                delay(250)
                if (done) return@repeat
            }
            h.cancel()
            val msgs = messages().takeLast(MAX_MESSAGES)
            val arr = JSONArray()
            msgs.forEach {
                arr.put(JSONObject().put("s", if (it.isOwn) "You" else it.senderName).put("t", it.plainText).put("ts", it.ts))
            }
            val plus = Sb.matrix.accountData.plus
            val name = plus.nickname(roomId) ?: info?.displayName ?: Sb.matrix.summary(roomId)?.realName ?: "Chat"
            val o = JSONObject()
                .put("name", name)
                .put("count", info?.notificationCount?.toLong() ?: 0L)
                .put("unread", (info?.numUnreadMessages?.toLong() ?: 0L) > 0 || info?.isMarkedUnread == true)
                .put("mention", (info?.highlightCount?.toLong() ?: 0L) > 0 || (info?.numUnreadMentions?.toLong() ?: 0L) > 0)
                .put("lines", arr)
            Prefs.putJson(dataKey(roomId), o)
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val v = RemoteViews(context.packageName, R.layout.widget_chat)
            val roomId = Prefs.getString(roomKey(widgetId))
            val switch = PendingIntent.getActivity(
                context, widgetId,
                Intent(context, ChatWidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_switch, switch)
            val service = Intent(context, ChatWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.widget_messages, service)
            v.setEmptyView(R.id.widget_messages, R.id.widget_empty)
            if (roomId == null) {
                v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_pick))
                v.setViewVisibility(R.id.widget_badge, View.GONE)
                v.setTextViewText(R.id.widget_empty, "Tap to pick a chat")
                v.setOnClickPendingIntent(R.id.widget_root, switch)
                v.setOnClickPendingIntent(R.id.widget_empty, switch)
                manager.updateAppWidget(widgetId, v)
                return
            }
            val d = Prefs.json(dataKey(roomId))
            v.setTextViewText(R.id.widget_title, d.optString("name").ifEmpty { Sb.matrix.summary(roomId)?.realName ?: "Chat" })
            val count = d.optLong("count")
            val mention = d.optBoolean("mention")
            if (count > 0 || mention) {
                v.setViewVisibility(R.id.widget_badge, View.VISIBLE)
                v.setTextViewText(R.id.widget_badge, (if (mention) "@" else "") + if (count > 0) count.toString() else "")
                v.setInt(R.id.widget_badge, "setBackgroundResource", if (mention) R.drawable.widget_badge_mention else R.drawable.widget_badge)
            } else v.setViewVisibility(R.id.widget_badge, View.GONE)
            v.setTextViewText(R.id.widget_empty, if (d.has("lines")) "No messages yet" else "Open Switchboard to load messages")
            val openIntent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(
                context, roomId.hashCode() + widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_title, open)
            v.setOnClickPendingIntent(R.id.widget_empty, open)
            // List rows fill in this template, which must be mutable.
            val template = PendingIntent.getActivity(
                context, roomId.hashCode() + widgetId + 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            v.setPendingIntentTemplate(R.id.widget_messages, template)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(widgetId, v)
        }
    }
}

/** Supplies the widget's scrollable message list. */
class ChatWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Message(val sender: String, val body: String, val ts: Long)

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var messages: List<Message> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            Prefs.init(context)
            val roomId = Prefs.getString(ChatWidget.roomKey(widgetId))
            val lines = roomId?.let { Prefs.json(ChatWidget.dataKey(it)).optJSONArray("lines") } ?: JSONArray()
            messages = (0 until lines.length()).mapNotNull { i ->
                lines.optJSONObject(i)?.let { Message(it.optString("s"), it.optString("t"), it.optLong("ts")) }
            }
        }

        override fun onDestroy() { messages = emptyList() }

        override fun getCount(): Int = messages.size

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_chat_item)
            val m = messages.getOrNull(position) ?: return v
            v.setTextViewText(R.id.item_sender, m.sender)
            v.setTextViewText(R.id.item_body, m.body)
            v.setTextViewText(
                R.id.item_time,
                if (m.ts <= 0L) "" else DateUtils.formatDateTime(
                    context, m.ts,
                    if (DateUtils.isToday(m.ts)) DateUtils.FORMAT_SHOW_TIME
                    else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                ),
            )
            v.setOnClickFillInIntent(R.id.item_root, Intent())
            return v
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}

/** Picks (or changes) the chat a widget shows. */
class ChatWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent {
            SwitchboardTheme {
                val c = Sbt.c
                val summaries by Sb.matrix.summaries.collectAsState()
                val order by Sb.matrix.order.collectAsState()
                val plus by Sb.matrix.accountData.state.collectAsState()
                var query by remember { mutableStateOf("") }
                val rooms = remember(summaries, order, query, plus) {
                    HomeData.allChats(summaries, order).filter { !it.isInvite && (query.isBlank() || plus.displayName(it).contains(query, true)) }
                }
                Column(Modifier.fillMaxSize().background(c.background).systemBarsPadding()) {
                    Column(Modifier.padding(20.dp)) {
                        Kicker("Widget")
                        Text("Pick a chat", style = Sbt.title.copy(fontSize = 28.sp), color = c.text)
                    }
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    if (summaries.isEmpty()) Text("Open Switchboard and sign in first.", color = c.textMuted, modifier = Modifier.padding(20.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rooms, key = { it.id }) { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { pick(widgetId, s.id) }.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(s.avatarUrl, plus.displayName(s), size = 40.dp, key = s.id)
                                Spacer(Modifier.width(14.dp))
                                Text(plus.displayName(s), fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = c.text)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(widgetId: Int, roomId: String) {
        Prefs.putString(ChatWidget.roomKey(widgetId), roomId)
        val manager = AppWidgetManager.getInstance(this)
        ChatWidget.render(this, manager, widgetId)
        ChatWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
