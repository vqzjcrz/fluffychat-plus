package app.switchboard.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Switchboard's own settings stored in the Matrix account (account data), so
 * they follow the account across devices and reinstalls. The event types are
 * the same ones the user's earlier client wrote, so nothing has to be set up
 * again.
 */
object PlusTypes {
    const val SPACE_PREFS = "im.fluffychat.plus.space_prefs"
    const val CUSTOM_ORDER = "im.fluffychat.plus.custom_order"
    const val SNOOZE = "im.fluffychat.plus.snooze"
    const val NICKNAMES = "im.fluffychat.plus.nicknames"
    const val PERSONAL_PINS = "im.fluffychat.plus.personal_pins"
    const val READ_ONLY = "im.fluffychat.plus.read_only"
    val ALL = listOf(SPACE_PREFS, CUSTOM_ORDER, SNOOZE, NICKNAMES, PERSONAL_PINS, READ_ONLY)
}

class AccountDataCache(private val m: MatrixManager) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(Plus(loadLocal()))
    val state: StateFlow<Plus> = _state
    val plus: Plus get() = _state.value

    private fun loadLocal(): Map<String, JSONObject> =
        PlusTypes.ALL.associateWith { Prefs.json("ad:$it") }

    suspend fun loadAll() {
        val c = m.client ?: return
        val map = HashMap(_state.value.raw)
        for (type in PlusTypes.ALL) {
            try {
                val raw = c.accountData(type) ?: continue
                val o = JSONObject(raw)
                map[type] = o
                Prefs.putJson("ad:$type", o)
            } catch (e: Exception) {
                Log.w(MatrixManager.TAG, "account data $type", e)
            }
        }
        _state.value = Plus(map)
    }

    /** Changes one account data event: locally right away, then on the server. */
    suspend fun update(type: String, change: (JSONObject) -> Unit) {
        val content = mutex.withLock {
            val copy = JSONObject(_state.value.obj(type).toString())
            change(copy)
            _state.value = Plus(_state.value.raw + (type to copy))
            Prefs.putJson("ad:$type", copy)
            copy
        }
        retryOnRateLimit { m.client?.setAccountData(type, content.toString()) }
    }
}

data class SpaceCategory(val id: String, val name: String, val collapsed: Boolean)

enum class SortMode(val key: String) { RECENT("recent"), UNREAD("unread"), CUSTOM("custom") }

/** Read access to the account data above. */
class Plus(val raw: Map<String, JSONObject>) {
    fun obj(type: String): JSONObject = raw[type] ?: JSONObject()
    private fun roomsMap(type: String): JSONObject = obj(type).optJSONObject("rooms") ?: JSONObject()
    private fun spaceMap(key: String): JSONObject = obj(PlusTypes.SPACE_PREFS).optJSONObject(key) ?: JSONObject()

    fun sortMode(spaceId: String): SortMode {
        val v = spaceMap("sort").optString(spaceId)
        return SortMode.entries.firstOrNull { it.key == v } ?: SortMode.RECENT
    }

    fun pins(spaceId: String): List<String> = spaceMap("pins").optJSONArray(spaceId).strings()

    fun spaceColor(spaceId: String): Int? {
        val o = spaceMap("colors")
        if (!o.has(spaceId)) return null
        return o.optLong(spaceId).toInt()
    }

    fun categories(spaceId: String): List<SpaceCategory> {
        val a = spaceMap("categories").optJSONArray(spaceId) ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val c = a.optJSONObject(i) ?: return@mapNotNull null
            val id = c.optString("id"); val name = c.optString("name")
            if (id.isEmpty()) null else SpaceCategory(id, name, c.optBoolean("collapsed"))
        }
    }

    fun categoryOf(spaceId: String): Map<String, String> {
        val o = spaceMap("category_of").optJSONObject(spaceId) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotEmpty() }
    }

    /** Where the chats without a category go among the categories (0 = above all). */
    fun chatsPosition(spaceId: String): Int {
        val o = spaceMap("chats_pos")
        val count = categories(spaceId).size
        if (!o.has(spaceId)) return 0
        return o.optInt(spaceId).coerceIn(0, count)
    }

    /** Category IDs in display order, with null where the uncategorized chats go. */
    fun blockOrder(spaceId: String): List<String?> {
        val order: MutableList<String?> = categories(spaceId).map { it.id }.toMutableList()
        order.add(chatsPosition(spaceId).coerceAtMost(order.size), null)
        return order
    }

    fun spaceOrder(): List<String> = obj(PlusTypes.CUSTOM_ORDER).optJSONArray("spaces").strings()

    fun childOrder(spaceId: String): List<String> =
        obj(PlusTypes.CUSTOM_ORDER).optJSONObject("space_children")?.optJSONArray(spaceId).strings()

    fun snoozedUntil(roomId: String): Long? {
        val o = roomsMap(PlusTypes.SNOOZE)
        if (!o.has(roomId)) return null
        val t = o.optLong(roomId)
        return if (t > System.currentTimeMillis()) t else null
    }

    fun nickname(roomId: String): String? = roomsMap(PlusTypes.NICKNAMES).optString(roomId).ifEmpty { null }

    fun personalPins(roomId: String): List<String> = roomsMap(PlusTypes.PERSONAL_PINS).optJSONArray(roomId).strings()

    fun readOnlyOverride(roomId: String): Boolean? {
        val o = roomsMap(PlusTypes.READ_ONLY)
        return if (o.has(roomId)) o.optBoolean(roomId) else null
    }

    /** The name to show for a chat: the user's own name for it, else its real name. */
    fun displayName(s: RoomSummary): String = nickname(s.id) ?: s.realName
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).ifEmpty { null } }
}

fun List<String>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

/** Changes to the account data above. */
object PlusEdit {
    private val ad get() = Sb.matrix.accountData

    private fun JSONObject.child(key: String): JSONObject =
        optJSONObject(key) ?: JSONObject().also { put(key, it) }

    suspend fun setSortMode(spaceId: String, mode: SortMode) = ad.update(PlusTypes.SPACE_PREFS) {
        it.child("sort").put(spaceId, mode.key)
    }

    suspend fun setPinned(spaceId: String, roomId: String, pinned: Boolean) = ad.update(PlusTypes.SPACE_PREFS) {
        val pins = it.child("pins")
        val list = pins.optJSONArray(spaceId).strings().toMutableList()
        list.remove(roomId)
        if (pinned) list.add(roomId)
        pins.put(spaceId, list.toJsonArray())
    }

    suspend fun setSpaceColor(spaceId: String, color: Int?) = ad.update(PlusTypes.SPACE_PREFS) {
        val colors = it.child("colors")
        if (color == null) colors.remove(spaceId) else colors.put(spaceId, color.toLong() and 0xFFFFFFFFL)
    }

    private fun categoriesJson(list: List<SpaceCategory>) = JSONArray().also { a ->
        list.forEach { c -> a.put(JSONObject().put("id", c.id).put("name", c.name).put("collapsed", c.collapsed)) }
    }

    suspend fun setCategories(spaceId: String, list: List<SpaceCategory>) = ad.update(PlusTypes.SPACE_PREFS) {
        it.child("categories").put(spaceId, categoriesJson(list))
    }

    suspend fun addCategory(spaceId: String, name: String): String {
        val id = "c" + System.currentTimeMillis().toString(36)
        val list = ad.plus.categories(spaceId) + SpaceCategory(id, name, false)
        setCategories(spaceId, list)
        return id
    }

    suspend fun renameCategory(spaceId: String, id: String, name: String) =
        setCategories(spaceId, ad.plus.categories(spaceId).map { if (it.id == id) it.copy(name = name) else it })

    suspend fun setCategoryCollapsed(spaceId: String, id: String, collapsed: Boolean) =
        setCategories(spaceId, ad.plus.categories(spaceId).map { if (it.id == id) it.copy(collapsed = collapsed) else it })

    suspend fun deleteCategory(spaceId: String, id: String) = ad.update(PlusTypes.SPACE_PREFS) {
        val list = Plus(mapOf(PlusTypes.SPACE_PREFS to it)).categories(spaceId).filter { c -> c.id != id }
        it.child("categories").put(spaceId, categoriesJson(list))
        val of = it.child("category_of").optJSONObject(spaceId)
        if (of != null) {
            val keys = of.keys().asSequence().toList()
            keys.forEach { k -> if (of.optString(k) == id) of.remove(k) }
        }
    }

    /** Moves a category up (negative) or down (positive), the uncategorized chats counting as one place. */
    suspend fun moveCategory(spaceId: String, id: String, delta: Int) = ad.update(PlusTypes.SPACE_PREFS) {
        val p = Plus(mapOf(PlusTypes.SPACE_PREFS to it))
        val order = p.blockOrder(spaceId).toMutableList()
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to < 0 || to >= order.size) return@update
        order.add(to, order.removeAt(from))
        val byId = p.categories(spaceId).associateBy { c -> c.id }
        it.child("categories").put(spaceId, categoriesJson(order.filterNotNull().mapNotNull { c -> byId[c] }))
        it.child("chats_pos").put(spaceId, order.indexOf(null))
    }

    suspend fun setCategoryOf(spaceId: String, roomIds: Collection<String>, categoryId: String?) = ad.update(PlusTypes.SPACE_PREFS) {
        val all = it.child("category_of")
        val map = all.optJSONObject(spaceId) ?: JSONObject().also { o -> all.put(spaceId, o) }
        roomIds.forEach { r -> if (categoryId == null) map.remove(r) else map.put(r, categoryId) }
    }

    suspend fun setSpaceOrder(ids: List<String>) = ad.update(PlusTypes.CUSTOM_ORDER) {
        it.put("spaces", ids.toJsonArray())
    }

    suspend fun setChildOrder(spaceId: String, ids: List<String>?) = ad.update(PlusTypes.CUSTOM_ORDER) {
        val m = it.child("space_children")
        if (ids == null) m.remove(spaceId) else m.put(spaceId, ids.toJsonArray())
    }

    suspend fun setSnooze(roomId: String, until: Long?) {
        ad.update(PlusTypes.SNOOZE) {
            val rooms = it.child("rooms")
            if (until == null) rooms.remove(roomId) else rooms.put(roomId, until)
            // Drop snoozes that already ended.
            val now = System.currentTimeMillis()
            rooms.keys().asSequence().toList().forEach { k -> if (rooms.optLong(k) < now) rooms.remove(k) }
        }
    }

    suspend fun setNickname(roomId: String, name: String?) = ad.update(PlusTypes.NICKNAMES) {
        val rooms = it.child("rooms")
        if (name.isNullOrBlank()) rooms.remove(roomId) else rooms.put(roomId, name.trim())
    }

    suspend fun setPersonalPin(roomId: String, eventId: String, pinned: Boolean) = ad.update(PlusTypes.PERSONAL_PINS) {
        val rooms = it.child("rooms")
        val list = rooms.optJSONArray(roomId).strings().toMutableList()
        list.remove(eventId)
        if (pinned) list.add(eventId)
        if (list.isEmpty()) rooms.remove(roomId) else rooms.put(roomId, list.toJsonArray())
    }

    /** Saves a read-only choice, or forgets it when it matches the default. */
    suspend fun setReadOnly(roomId: String, readOnly: Boolean, default: Boolean) = ad.update(PlusTypes.READ_ONLY) {
        val rooms = it.child("rooms")
        if (readOnly == default) rooms.remove(roomId) else rooms.put(roomId, readOnly)
    }
}
