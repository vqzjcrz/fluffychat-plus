package app.switchboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
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
        private val LINES = intArrayOf(R.id.widget_line0, R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4, R.id.widget_line5)

        fun roomKey(widgetId: Int) = "widget_room_$widgetId"
        private fun dataKey(roomId: String) = "widget_data_$roomId"

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
            delay(250)
            if (synchronized(items) { items.size } < 8) runCatching { t.paginateBackwards(20u) }
            delay(250)
            h.cancel()
            val own = Sb.matrix.userId ?: ""
            val msgs = synchronized(items) { items.toList() }.mapNotNull { TimelineMapper.map(it, own) as? TEvent }.filter { it.isMessage }.takeLast(6)
            val arr = JSONArray()
            msgs.forEach { arr.put(JSONObject().put("s", if (it.isOwn) "You" else it.senderName).put("t", it.plainText)) }
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
            if (roomId == null) {
                v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_pick))
                v.setOnClickPendingIntent(R.id.widget_root, switch)
                LINES.forEach { v.setViewVisibility(it, View.GONE) }
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
            val lines = d.optJSONArray("lines") ?: JSONArray()
            val n = lines.length()
            LINES.forEachIndexed { i, id ->
                val idx = n - LINES.size + i
                if (idx < 0) { v.setViewVisibility(id, View.GONE); return@forEachIndexed }
                val o = lines.optJSONObject(idx) ?: return@forEachIndexed
                val s = o.optString("s"); val t = o.optString("t")
                val sb = SpannableStringBuilder("$s  $t")
                sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                v.setTextViewText(id, sb)
                v.setViewVisibility(id, View.VISIBLE)
            }
            val open = PendingIntent.getActivity(
                context, roomId.hashCode() + widgetId,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(widgetId, v)
        }
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
