package app.switchboard.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableSharedFlow

sealed interface Screen {
    data object Home : Screen
    data class Chat(val roomId: String, val eventId: String? = null) : Screen
    data class RoomDetails(val roomId: String) : Screen
    data class Members(val roomId: String) : Screen
    data class SpaceMedia(val spaceId: String) : Screen
    data class UserProfile(val userId: String, val roomId: String? = null) : Screen
    data object Settings : Screen
    data object SettingsStyle : Screen
    data object SettingsChat : Screen
    data object SettingsNotifications : Screen
    data object SettingsSecurity : Screen
    data object SettingsDevices : Screen
    data object SettingsIgnored : Screen
    data object SettingsAbout : Screen
    data object Verify : Screen
    data object NewChat : Screen
    data class NewGroup(val space: Boolean, val parentSpaceId: String? = null) : Screen
    data object JoinRoom : Screen
    data class Invites(val spaceId: String?) : Screen
    data class Share(val text: String?, val uris: List<android.net.Uri>) : Screen
    data class EditRoom(val roomId: String) : Screen
    data class SpaceRooms(val spaceId: String) : Screen
    data class Search(val roomId: String) : Screen
}

/** A simple back stack of screens. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()

    /** Space selected in the rail (null = all chats). */
    val selectedSpace = mutableStateOf<String?>(null)

    /** Snackbar messages. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun push(s: Screen) {
        if (stack.lastOrNull() == s) return
        stack.add(s)
    }

    /** Opens a chat, replacing another open chat instead of stacking them. */
    fun openChat(roomId: String, eventId: String? = null) {
        while (stack.size > 1 && stack.last() !is Screen.Home) stack.removeAt(stack.size - 1)
        stack.add(Screen.Chat(roomId, eventId))
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        return true
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun toast(text: String) { messages.tryEmit(text) }
}

val LocalNav = compositionLocalOf<Nav> { error("No Nav") }
