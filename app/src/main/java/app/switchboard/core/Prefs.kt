package app.switchboard.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Settings kept on this phone only. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("switchboard", Context.MODE_PRIVATE)
        _theme.value = sp.getString(KEY_THEME, "mono") ?: "mono"
        _seedColor.value = sp.getInt(KEY_SEED, 0xFF6750A4.toInt())
        _fontScale.value = sp.getFloat(KEY_FONT_SCALE, 1f)
        _swipeToReplyLeft.value = sp.getBoolean(KEY_SWIPE_LEFT, false)
        _showReadReceipts.value = sp.getBoolean(KEY_RECEIPTS, true)
        _sendOnEnter.value = sp.getBoolean(KEY_ENTER, false)
        _hideStateEvents.value = sp.getBoolean(KEY_HIDE_STATE, true)
        _autoplayGifs.value = sp.getBoolean(KEY_GIFS, true)
        _linkPreviews.value = sp.getBoolean(KEY_PREVIEWS, true)
        _sendTyping.value = sp.getBoolean(KEY_TYPING, false)
        _doubleTapReaction.value = sp.getString(KEY_DOUBLE_TAP, "\u2764\uFE0F") ?: ""
        _railLabels.value = sp.getBoolean(KEY_RAIL_LABELS, false)
    }

    private const val KEY_THEME = "theme_preset"
    private const val KEY_SEED = "seed_color"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_SWIPE_LEFT = "swipe_reply_left_to_right"
    private const val KEY_RECEIPTS = "show_read_receipts"
    private const val KEY_ENTER = "send_on_enter"
    private const val KEY_HIDE_STATE = "hide_state_events"
    private const val KEY_GIFS = "autoplay_gifs"
    private const val KEY_PREVIEWS = "link_previews"
    private const val KEY_TYPING = "send_typing"
    private const val KEY_DOUBLE_TAP = "double_tap_reaction"
    private const val KEY_RAIL_LABELS = "rail_labels"

    private val _theme = MutableStateFlow("mono")
    val theme: StateFlow<String> = _theme
    fun setTheme(id: String) { _theme.value = id; sp.edit().putString(KEY_THEME, id).apply() }

    private val _seedColor = MutableStateFlow(0xFF6750A4.toInt())
    val seedColor: StateFlow<Int> = _seedColor
    fun setSeedColor(c: Int) { _seedColor.value = c; sp.edit().putInt(KEY_SEED, c).apply() }

    private val _fontScale = MutableStateFlow(1f)
    val fontScale: StateFlow<Float> = _fontScale
    fun setFontScale(v: Float) { _fontScale.value = v; sp.edit().putFloat(KEY_FONT_SCALE, v).apply() }

    private val _swipeToReplyLeft = MutableStateFlow(false)
    /** True when swiping a message left-to-right replies (then the spaces panel swipe is off). */
    val swipeToReplyLeftToRight: StateFlow<Boolean> = _swipeToReplyLeft
    fun setSwipeToReplyLeftToRight(v: Boolean) { _swipeToReplyLeft.value = v; sp.edit().putBoolean(KEY_SWIPE_LEFT, v).apply() }

    private val _showReadReceipts = MutableStateFlow(true)
    val showReadReceipts: StateFlow<Boolean> = _showReadReceipts
    fun setShowReadReceipts(v: Boolean) { _showReadReceipts.value = v; sp.edit().putBoolean(KEY_RECEIPTS, v).apply() }

    private val _sendOnEnter = MutableStateFlow(false)
    val sendOnEnter: StateFlow<Boolean> = _sendOnEnter
    fun setSendOnEnter(v: Boolean) { _sendOnEnter.value = v; sp.edit().putBoolean(KEY_ENTER, v).apply() }

    private val _hideStateEvents = MutableStateFlow(true)
    val hideStateEvents: StateFlow<Boolean> = _hideStateEvents
    fun setHideStateEvents(v: Boolean) { _hideStateEvents.value = v; sp.edit().putBoolean(KEY_HIDE_STATE, v).apply() }

    private val _autoplayGifs = MutableStateFlow(true)
    val autoplayGifs: StateFlow<Boolean> = _autoplayGifs
    fun setAutoplayGifs(v: Boolean) { _autoplayGifs.value = v; sp.edit().putBoolean(KEY_GIFS, v).apply() }

    private val _linkPreviews = MutableStateFlow(true)
    val linkPreviews: StateFlow<Boolean> = _linkPreviews
    fun setLinkPreviews(v: Boolean) { _linkPreviews.value = v; sp.edit().putBoolean(KEY_PREVIEWS, v).apply() }

    /** Whether others (and bridged services) see when you're typing. Off by default. */
    private val _sendTyping = MutableStateFlow(false)
    val sendTyping: StateFlow<Boolean> = _sendTyping
    fun setSendTyping(v: Boolean) { _sendTyping.value = v; sp.edit().putBoolean(KEY_TYPING, v).apply() }

    /** Emoji to react with when double-tapping a message; empty turns it off. */
    private val _doubleTapReaction = MutableStateFlow("\u2764\uFE0F")
    val doubleTapReaction: StateFlow<String> = _doubleTapReaction
    fun setDoubleTapReaction(v: String) { _doubleTapReaction.value = v; sp.edit().putString(KEY_DOUBLE_TAP, v).apply() }

    private val _railLabels = MutableStateFlow(false)
    val railLabels: StateFlow<Boolean> = _railLabels

    // --- simple values ---
    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    fun getBool(key: String, def: Boolean = false) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    fun getLong(key: String, def: Long = 0) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()

    /** The space that was open last (null = all chats). */
    var lastSpace: String?
        get() = sp.getString("last_space", null)
        set(v) = putString("last_space", v)

    /** A space to always open on start, instead of the last one. */
    var startSpace: String?
        get() = sp.getString("start_space", null)
        set(v) = putString("start_space", v)

    fun json(key: String): JSONObject = try {
        JSONObject(sp.getString(key, null) ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun putJson(key: String, o: JSONObject) = putString(key, o.toString())

    fun jsonArray(key: String): JSONArray = try {
        JSONArray(sp.getString(key, null) ?: "[]")
    } catch (_: Exception) { JSONArray() }

    fun putJsonArray(key: String, a: JSONArray) = putString(key, a.toString())

    fun clearAll() {
        val theme = _theme.value
        sp.edit().clear().apply()
        setTheme(theme)
    }
}
