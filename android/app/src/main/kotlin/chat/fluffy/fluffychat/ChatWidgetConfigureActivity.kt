package chat.fluffy.fluffychat

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

/**
 * Lets the user pick which chat a FluffyChat+ widget shows. Opens when the
 * widget is added, from the widget's swap button, and from "Reconfigure" on
 * Android 12 and newer.
 */
class ChatWidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out cancels adding the widget.
        setResult(RESULT_CANCELED)

        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.fcp_widget_configure)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.75).toInt(),
        )

        val rooms = WidgetStore.rooms(this)
        val adapter = RoomAdapter(rooms)
        val list = findViewById<ListView>(R.id.configure_list)
        val empty = findViewById<TextView>(R.id.configure_empty)
        val search = findViewById<EditText>(R.id.configure_search)

        empty.setText(if (rooms.isEmpty()) R.string.fcp_widget_no_rooms else R.string.fcp_widget_no_match)
        list.adapter = adapter
        list.emptyView = empty
        list.setOnItemClickListener { _, _, position, _ -> select(adapter.getItem(position)) }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    adapter.filter(s?.toString() ?: "")
                }
            },
        )
    }

    private fun select(room: WidgetRoom) {
        WidgetStore.setWidgetRoom(this, widgetId, room.id)
        ChatWidgetProvider.updateAll(this)
        WidgetBridge.requestRefresh()
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }

    private inner class RoomAdapter(private val all: List<WidgetRoom>) : BaseAdapter() {
        private var shown: List<WidgetRoom> = all

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) {
                all
            } else {
                all.filter { it.name.lowercase().contains(q) || it.sub.lowercase().contains(q) }
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = shown.size

        override fun getItem(position: Int): WidgetRoom = shown[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val room = shown[position]
            view.findViewById<TextView>(android.R.id.text1).text = room.name
            val sub = view.findViewById<TextView>(android.R.id.text2)
            sub.text = room.sub
            sub.visibility = if (room.sub.isEmpty()) View.GONE else View.VISIBLE
            return view
        }
    }
}
