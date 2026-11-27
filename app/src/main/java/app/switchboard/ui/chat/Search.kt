package app.switchboard.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.switchboard.core.Api
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.common.Hairline
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Hit(val eventId: String, val sender: String, val body: String, val ts: Long)

/** Server-side message search in one chat (works for chats that aren't end-to-end encrypted). */
@Composable
fun SearchScreen(roomId: String) {
    val c = Sbt.c
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val summary = Sb.matrix.summary(roomId)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { hits = null; return@LaunchedEffect }
        delay(450)
        busy = true; error = null
        hits = try {
            val body = JSONObject().put("search_categories", JSONObject().put("room_events", JSONObject()
                .put("search_term", q).put("order_by", "recent")
                .put("filter", JSONObject().put("rooms", JSONArray().put(roomId)).put("limit", 50))))
            val res = Api.post("/_matrix/client/v3/search", body)
            val results = res.optJSONObject("search_categories")?.optJSONObject("room_events")?.optJSONArray("results") ?: JSONArray()
            (0 until results.length()).mapNotNull { i ->
                val ev = results.optJSONObject(i)?.optJSONObject("result") ?: return@mapNotNull null
                Hit(ev.optString("event_id"), ev.optString("sender"), ev.optJSONObject("content")?.optString("body") ?: "", ev.optLong("origin_server_ts"))
            }
        } catch (e: Exception) { error = e.message; emptyList() }
        busy = false
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Search", kicker = summary?.let { Sb.matrix.accountData.plus.displayName(it) }, onBack = { nav.pop() })
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search messages") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (summary?.isEncrypted == true) {
            Text("This chat is end-to-end encrypted, so the server can't search it.", style = Sbt.small, color = c.textMuted, modifier = Modifier.padding(16.dp))
        }
        if (busy) CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(20.dp))
        error?.let { Text(it, color = c.danger, style = Sbt.small, modifier = Modifier.padding(16.dp)) }
        val list = hits
        if (list != null && list.isEmpty() && !busy) Text("No messages found.", style = Sbt.body, color = c.textMuted, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list ?: emptyList(), key = { it.eventId }) { h ->
                Column(Modifier.fillMaxWidth().clickable { nav.openChat(roomId, h.eventId) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Kicker(h.sender.removePrefix("@").substringBefore(':') + " · " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(h.ts)))
                    Spacer(Modifier.height(2.dp))
                    Text(h.body, style = Sbt.body, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Hairline()
            }
        }
    }
}

