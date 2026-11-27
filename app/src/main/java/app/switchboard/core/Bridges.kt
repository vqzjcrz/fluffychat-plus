package app.switchboard.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/** What a bridge says about a room: which network it connects to. */
data class BridgeInfo(val protocol: String, val name: String)

/**
 * Finds out which rooms are bridged (Discord, Signal, WhatsApp...) from the
 * bridges' m.bridge state events. Looked up once per room and remembered.
 */
object Bridges {
    private const val CACHE = "bridges_v1"
    private val _info = MutableStateFlow<Map<String, BridgeInfo?>>(emptyMap())
    val info: StateFlow<Map<String, BridgeInfo?>> = _info
    private val inFlight = HashSet<String>()
    private val gate = Semaphore(3)
    private var started = false

    fun init(m: MatrixManager) {
        if (!started) {
            started = true
            val o = Prefs.json(CACHE)
            val map = HashMap<String, BridgeInfo?>()
            o.keys().forEach { k ->
                val v = o.optJSONObject(k)
                map[k] = v?.let { BridgeInfo(it.optString("p"), it.optString("n")) }
            }
            _info.value = map
            m.scope.launch {
                m.summaries.collect { rooms -> rooms.keys.forEach { lookup(m, it) } }
            }
        }
    }

    fun get(roomId: String): BridgeInfo? = _info.value[roomId]

    fun isDiscord(roomId: String): Boolean = get(roomId)?.protocol?.contains("discord", ignoreCase = true) == true

    /** Fast guess from member IDs while the real lookup hasn't happened. */
    fun guess(userIds: List<String>): String? {
        for (u in userIds) {
            val local = u.removePrefix("@").substringBefore(':')
            when {
                local.startsWith("discord_") -> return "Discord"
                local.startsWith("signal_") -> return "Signal"
                local.startsWith("whatsapp_") -> return "WhatsApp"
                local.startsWith("instagram_") || local.startsWith("meta_") || local.startsWith("ig_") -> return "Instagram"
                local.startsWith("telegram_") -> return "Telegram"
            }
        }
        return null
    }

    private fun lookup(m: MatrixManager, roomId: String) {
        synchronized(inFlight) {
            if (_info.value.containsKey(roomId) || !inFlight.add(roomId)) return
        }
        m.scope.launch {
            gate.withPermit {
                val result = try { fetch(roomId) } catch (e: Exception) {
                    Log.w(MatrixManager.TAG, "bridge lookup $roomId: ${e.message}")
                    synchronized(inFlight) { inFlight.remove(roomId) }
                    return@withPermit
                }
                _info.value = _info.value + (roomId to result)
                synchronized(inFlight) { inFlight.remove(roomId) }
                save()
            }
        }
    }

    private suspend fun fetch(roomId: String): BridgeInfo? {
        val conn = Api.request("GET", "/_matrix/client/v3/rooms/${Api.enc(roomId)}/state", null)
        // The state endpoint returns an array; Api wraps non-object bodies poorly, so use the raw text.
        return parse(conn)
    }

    private fun parse(o: JSONObject): BridgeInfo? {
        val arr: JSONArray = o.optJSONArray("__array") ?: return null
        for (i in 0 until arr.length()) {
            val ev = arr.optJSONObject(i) ?: continue
            val type = ev.optString("type")
            if (type != "m.bridge" && type != "uk.half-shot.bridge") continue
            val content = ev.optJSONObject("content") ?: continue
            val protocol = content.optJSONObject("protocol") ?: continue
            val id = protocol.optString("id")
            if (id.isEmpty()) continue
            val name = protocol.optString("displayname").ifEmpty { id.replaceFirstChar { it.uppercase() } }
            return BridgeInfo(id, name)
        }
        return null
    }

    private fun save() {
        val o = JSONObject()
        _info.value.forEach { (k, v) ->
            o.put(k, if (v == null) JSONObject.NULL else JSONObject().put("p", v.protocol).put("n", v.name))
        }
        Prefs.putJson(CACHE, o)
    }
}
