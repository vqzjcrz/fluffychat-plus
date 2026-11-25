package chat.fluffy.fluffychat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

// FluffyChat+ home screen widget: each widget shows the recent messages of
// one chat the user picks. The Flutter side pushes the data through
// WidgetBridge; the widget itself only reads what was stored.

data class WidgetRoom(val id: String, val name: String, val sub: String)

data class WidgetMessage(val sender: String, val body: String, val time: Long)

object WidgetStore {
    private const val PREFS = "fluffychat_plus_widgets"
    private const val KEY_ROOMS = "rooms"
    private const val PREFIX_WIDGET = "widget_"
    private const val PREFIX_ROOM = "room_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setRooms(context: Context, json: String) {
        prefs(context).edit().putString(KEY_ROOMS, json).apply()
    }

    private fun roomsArray(context: Context): JSONArray =
        try {
            JSONArray(prefs(context).getString(KEY_ROOMS, null) ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

    /** All chats the user can pick, most recently active first. */
    fun rooms(context: Context): List<WidgetRoom> {
        val array = roomsArray(context)
        val result = ArrayList<WidgetRoom>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optString("id")
            if (id.isEmpty()) continue
            result.add(WidgetRoom(id, entry.optString("name", id), entry.optString("sub")))
        }
        return result
    }

    /** The chat list entry for [roomId], with its last message. */
    fun roomSummary(context: Context, roomId: String): JSONObject? {
        val array = roomsArray(context)
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            if (entry.optString("id") == roomId) return entry
        }
        return null
    }

    fun setRoomData(context: Context, roomId: String, json: String) {
        prefs(context).edit().putString(PREFIX_ROOM + roomId, json).apply()
    }

    fun roomDataRaw(context: Context, roomId: String): String? =
        prefs(context).getString(PREFIX_ROOM + roomId, null)

    fun roomData(context: Context, roomId: String): JSONObject? {
        val raw = roomDataRaw(context, roomId) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun messages(context: Context, roomId: String): List<WidgetMessage> {
        val array = roomData(context, roomId)?.optJSONArray("messages")
            ?: roomSummary(context, roomId)?.optJSONObject("last")?.let { JSONArray().put(it) }
            ?: return emptyList()
        val result = ArrayList<WidgetMessage>(array.length())
        for (i in 0 until array.length()) {
            val message = array.optJSONObject(i) ?: continue
            result.add(
                WidgetMessage(
                    message.optString("sender"),
                    message.optString("body"),
                    message.optLong("time"),
                ),
            )
        }
        return result
    }

    fun setWidgetRoom(context: Context, widgetId: Int, roomId: String) {
        prefs(context).edit().putString(PREFIX_WIDGET + widgetId, roomId).apply()
    }

    fun roomForWidget(context: Context, widgetId: Int): String? =
        prefs(context).getString(PREFIX_WIDGET + widgetId, null)

    fun removeWidgets(context: Context, widgetIds: IntArray) {
        val editor = prefs(context).edit()
        for (id in widgetIds) editor.remove(PREFIX_WIDGET + id)
        editor.apply()
    }

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ChatWidgetProvider::class.java))

    /** Chats that have a widget. Drops stored messages of chats that no longer do. */
    fun configuredRoomIds(context: Context): List<String> {
        val ids = widgetIds(context).toList().mapNotNull { roomForWidget(context, it) }.distinct()
        val prefs = prefs(context)
        val stale = prefs.all.keys.filter {
            it.startsWith(PREFIX_ROOM) && it.removePrefix(PREFIX_ROOM) !in ids
        }
        if (stale.isNotEmpty()) {
            val editor = prefs.edit()
            for (key in stale) editor.remove(key)
            editor.apply()
        }
        return ids
    }
}

/** Method channel the Dart side uses to feed the widgets. */
object WidgetBridge {
    private const val CHANNEL = "im.fluffychat.plus/widgets"
    private var channel: MethodChannel? = null

    /** Asks the app, if it's running, to send fresh data for all widgets. */
    fun requestRefresh() {
        try {
            channel?.invokeMethod("requestRefresh", null)
        } catch (e: Exception) {
            // The app isn't running; it updates the widgets when it starts.
        }
    }

    fun register(context: Context, engine: FlutterEngine) {
        val app = context.applicationContext
        val methodChannel = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
        channel = methodChannel
        methodChannel.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "configuredRooms" -> result.success(WidgetStore.configuredRoomIds(app))
                    "setRooms" -> {
                        WidgetStore.setRooms(app, call.arguments as? String ?: "[]")
                        result.success(null)
                    }
                    "getRoomData" -> {
                        val roomId = call.arguments as? String
                        result.success(if (roomId == null) null else WidgetStore.roomDataRaw(app, roomId))
                    }
                    "setRoomData" -> {
                        val roomId = call.argument<String>("roomId")
                        val data = call.argument<String>("data")
                        if (roomId != null && data != null) WidgetStore.setRoomData(app, roomId, data)
                        result.success(null)
                    }
                    "refresh" -> {
                        ChatWidgetProvider.updateAll(app)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("widget", e.message, null)
            }
        }
    }
}

class ChatWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_messages)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetStore.removeWidgets(context, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = WidgetStore.widgetIds(context)
            if (ids.isEmpty()) return
            for (id in ids) updateWidget(context, manager, id)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_messages)
        }

        private fun immutableFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // List items fill in the template, which needs a mutable PendingIntent.
        private fun mutableFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        /** Opens the chat through the matrix: link handling FluffyChat already has. */
        private fun openRoomIntent(context: Context, roomId: String): Intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("matrix:roomid/" + Uri.encode(roomId.removePrefix("!"), ":")),
            ).apply {
                setClass(context, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        private fun configureIntent(context: Context, widgetId: Int): Intent =
            Intent(context, ChatWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        @Suppress("DEPRECATION")
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.fcp_chat_widget)

            val configure = PendingIntent.getActivity(
                context,
                widgetId * 3,
                configureIntent(context, widgetId),
                immutableFlags(),
            )
            views.setOnClickPendingIntent(R.id.widget_change, configure)

            val serviceIntent = Intent(context, ChatWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_messages, serviceIntent)
            views.setEmptyView(R.id.widget_messages, R.id.widget_empty)

            val roomId = WidgetStore.roomForWidget(context, widgetId)
            if (roomId == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.fcp_widget_label))
                views.setViewVisibility(R.id.widget_unread, View.GONE)
                views.setTextViewText(
                    R.id.widget_empty,
                    context.getString(R.string.fcp_widget_not_configured),
                )
                views.setOnClickPendingIntent(R.id.widget_title, configure)
                views.setOnClickPendingIntent(R.id.widget_empty, configure)
                manager.updateAppWidget(widgetId, views)
                return
            }

            val data = WidgetStore.roomData(context, roomId)
            val summary = WidgetStore.roomSummary(context, roomId)
            val name = data?.optString("name")?.takeIf { it.isNotEmpty() }
                ?: summary?.optString("name")?.takeIf { it.isNotEmpty() }
                ?: roomId
            val unread = data?.optInt("unread", 0) ?: summary?.optInt("unread", 0) ?: 0
            val highlight = data?.optInt("highlight", 0) ?: summary?.optInt("highlight", 0) ?: 0

            views.setTextViewText(R.id.widget_title, name)
            if (unread > 0) {
                views.setViewVisibility(R.id.widget_unread, View.VISIBLE)
                views.setTextViewText(R.id.widget_unread, if (unread > 99) "99+" else unread.toString())
                views.setInt(
                    R.id.widget_unread,
                    "setBackgroundResource",
                    if (highlight > 0) R.drawable.fcp_widget_badge_mention else R.drawable.fcp_widget_badge,
                )
                views.setTextColor(
                    R.id.widget_unread,
                    context.getColor(
                        if (highlight > 0) R.color.fcp_widget_on_mention else R.color.fcp_widget_on_badge,
                    ),
                )
            } else {
                views.setViewVisibility(R.id.widget_unread, View.GONE)
            }
            views.setTextViewText(
                R.id.widget_empty,
                context.getString(
                    if (data == null && summary == null) {
                        R.string.fcp_widget_open_app
                    } else {
                        R.string.fcp_widget_no_messages
                    },
                ),
            )

            val open = PendingIntent.getActivity(
                context,
                widgetId * 3 + 1,
                openRoomIntent(context, roomId),
                immutableFlags(),
            )
            views.setOnClickPendingIntent(R.id.widget_title, open)
            views.setOnClickPendingIntent(R.id.widget_empty, open)
            val template = PendingIntent.getActivity(
                context,
                widgetId * 3 + 2,
                openRoomIntent(context, roomId),
                mutableFlags(),
            )
            views.setPendingIntentTemplate(R.id.widget_messages, template)

            manager.updateAppWidget(widgetId, views)
        }
    }
}

class ChatWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ChatWidgetFactory(
            applicationContext,
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
}

class ChatWidgetFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var messages: List<WidgetMessage> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val roomId = WidgetStore.roomForWidget(context, widgetId)
        messages = if (roomId == null) emptyList() else WidgetStore.messages(context, roomId)
    }

    override fun onDestroy() {
        messages = emptyList()
    }

    override fun getCount(): Int = messages.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.fcp_chat_widget_item)
        val message = messages.getOrNull(position) ?: return views
        views.setTextViewText(R.id.item_sender, message.sender)
        views.setTextViewText(R.id.item_body, message.body)
        val time = if (message.time <= 0L) {
            ""
        } else {
            DateUtils.formatDateTime(
                context,
                message.time,
                if (DateUtils.isToday(message.time)) {
                    DateUtils.FORMAT_SHOW_TIME
                } else {
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
                },
            )
        }
        views.setTextViewText(R.id.item_time, time)
        views.setOnClickFillInIntent(R.id.item_root, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
