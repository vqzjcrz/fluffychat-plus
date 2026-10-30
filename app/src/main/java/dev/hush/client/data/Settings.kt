package dev.hush.client.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MessageDensity { COZY, COMPACT }

enum class AvatarShape { CIRCLE, ROUNDED, SQUARE }

data class AppSettings(
    val themeId: String = "midnight",
    /** ARGB accent override, or null to use the theme's own accent. */
    val accent: Long? = null,
    val density: MessageDensity = MessageDensity.COZY,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val showAvatars: Boolean = true,
    val fontScale: Float = 1f,
    val use24h: Boolean = false,
    val showEmbeds: Boolean = true,
    val showMedia: Boolean = true,
    val animateMedia: Boolean = true,
    val sendTyping: Boolean = true,
    val syncReadState: Boolean = true,
    val notifications: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = longPreferencesKey("accent")
        val DENSITY = stringPreferencesKey("density")
        val AVATAR_SHAPE = stringPreferencesKey("avatar_shape")
        val SHOW_AVATARS = booleanPreferencesKey("show_avatars")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val USE_24H = booleanPreferencesKey("use_24h")
        val SHOW_EMBEDS = booleanPreferencesKey("show_embeds")
        val SHOW_MEDIA = booleanPreferencesKey("show_media")
        val ANIMATE_MEDIA = booleanPreferencesKey("animate_media")
        val SEND_TYPING = booleanPreferencesKey("send_typing")
        val SYNC_READ = booleanPreferencesKey("sync_read")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeId = p[Keys.THEME] ?: d.themeId,
            accent = p[Keys.ACCENT],
            density = p[Keys.DENSITY]?.let { v -> MessageDensity.entries.firstOrNull { it.name == v } } ?: d.density,
            avatarShape = p[Keys.AVATAR_SHAPE]?.let { v -> AvatarShape.entries.firstOrNull { it.name == v } } ?: d.avatarShape,
            showAvatars = p[Keys.SHOW_AVATARS] ?: d.showAvatars,
            fontScale = p[Keys.FONT_SCALE] ?: d.fontScale,
            use24h = p[Keys.USE_24H] ?: d.use24h,
            showEmbeds = p[Keys.SHOW_EMBEDS] ?: d.showEmbeds,
            showMedia = p[Keys.SHOW_MEDIA] ?: d.showMedia,
            animateMedia = p[Keys.ANIMATE_MEDIA] ?: d.animateMedia,
            sendTyping = p[Keys.SEND_TYPING] ?: d.sendTyping,
            syncReadState = p[Keys.SYNC_READ] ?: d.syncReadState,
            notifications = p[Keys.NOTIFICATIONS] ?: d.notifications,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[Keys.THEME] = s.themeId
        val accent = s.accent
        if (accent != null) p[Keys.ACCENT] = accent else p.remove(Keys.ACCENT)
        p[Keys.DENSITY] = s.density.name
        p[Keys.AVATAR_SHAPE] = s.avatarShape.name
        p[Keys.SHOW_AVATARS] = s.showAvatars
        p[Keys.FONT_SCALE] = s.fontScale
        p[Keys.USE_24H] = s.use24h
        p[Keys.SHOW_EMBEDS] = s.showEmbeds
        p[Keys.SHOW_MEDIA] = s.showMedia
        p[Keys.ANIMATE_MEDIA] = s.animateMedia
        p[Keys.SEND_TYPING] = s.sendTyping
        p[Keys.SYNC_READ] = s.syncReadState
        p[Keys.NOTIFICATIONS] = s.notifications
    }
}
