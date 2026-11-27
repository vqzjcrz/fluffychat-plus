package app.switchboard.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TypingNotificationsListener

/** Who's typing in the chats on screen, for the chat list previews. */
object Typing {
    private val _names = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** Room ID to names of people typing there. */
    val names: StateFlow<Map<String, List<String>>> = _names
    private val handles = HashMap<String, TaskHandle>()
    private val nameCache = HashMap<String, String>()

    /** Watches these rooms (and stops watching others). */
    @Synchronized
    fun watch(roomIds: Collection<String>) {
        val want = roomIds.take(60).toSet()
        handles.keys.filter { it !in want }.forEach { id ->
            handles.remove(id)?.cancel()
            _names.update { it - id }
        }
        val own = Sb.matrix.userId
        for (id in want) {
            if (id in handles) continue
            val room = Sb.matrix.room(id) ?: continue
            try {
                handles[id] = room.subscribeToTypingNotifications(object : TypingNotificationsListener {
                    override fun call(typingUserIds: List<String>) {
                        val users = typingUserIds.filter { it != own }
                        if (users.isEmpty()) { _names.update { it - id }; return }
                        Sb.matrix.scope.launch {
                            val names = users.map { u ->
                                nameCache[u] ?: (runCatching { room.memberDisplayName(u) }.getOrNull() ?: Previews.localpart(u)).also { nameCache[u] = it }
                            }
                            _names.update { it + (id to names) }
                        }
                    }
                })
            } catch (_: Exception) {}
        }
    }

    fun text(names: List<String>?): String? = when {
        names.isNullOrEmpty() -> null
        names.size == 1 -> "${names[0]} is typing…"
        names.size == 2 -> "${names[0]} and ${names[1]} are typing…"
        else -> "Several people are typing…"
    }
}
