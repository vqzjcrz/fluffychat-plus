package app.switchboard.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.matrix.rustcomponents.sdk.ReceiptType

/** Progress of a bulk action, shown in a dialog. */
data class BulkProgress(val title: String, val done: Int, val total: Int, val note: String? = null)

/** Things the user can do to chats, one at a time or many at once. */
object RoomActions {
    private val m get() = Sb.matrix

    private val _progress = MutableStateFlow<BulkProgress?>(null)
    val progress: StateFlow<BulkProgress?> = _progress

    suspend fun markRead(roomId: String) {
        val r = m.room(roomId) ?: return
        retryOnRateLimit { r.markAsRead(Receipts.readType) }
        if (m.summary(roomId)?.markedUnread == true) runCatching { r.setUnreadFlag(false) }
    }

    suspend fun markUnread(roomId: String) {
        val r = m.room(roomId) ?: return
        retryOnRateLimit { r.setUnreadFlag(true) }
    }

    suspend fun leave(roomId: String) {
        val r = m.room(roomId) ?: return
        retryOnRateLimit { r.leave() }
    }

    suspend fun join(roomId: String) {
        val r = m.room(roomId) ?: return
        retryOnRateLimit { r.join() }
    }

    suspend fun setReadOnly(s: RoomSummary, readOnly: Boolean) {
        PlusEdit.setReadOnly(s.id, readOnly, Bridges.isDiscord(s.id))
    }

    /**
     * Puts rooms in [toSpace]. When [fromSpace] can be edited, they're taken
     * out of it; otherwise they're added to the new space and stay where they were.
     */
    suspend fun moveToSpace(roomIds: List<String>, toSpace: String, fromSpace: String?) {
        val client = m.client ?: return
        val spaces = client.spaceService()
        val canEditFrom = fromSpace != null && m.summary(fromSpace)?.canEditSpace == true
        runBulk("Moving chats", roomIds) { id ->
            spaces.addChildToSpace(id, toSpace)
            if (canEditFrom && fromSpace != toSpace) spaces.removeChildFromSpace(id, fromSpace!!)
        }
        m.reloadSpace(toSpace)
        if (fromSpace != null) m.reloadSpace(fromSpace)
    }

    suspend fun removeFromSpace(roomId: String, spaceId: String) {
        val spaces = m.client?.spaceService() ?: return
        retryOnRateLimit { spaces.removeChildFromSpace(roomId, spaceId) }
        m.reloadSpace(spaceId)
    }

    suspend fun bulkLeave(ids: List<String>) = runBulk("Leaving chats", ids) { leave(it) }
    suspend fun bulkMarkRead(ids: List<String>) = runBulk("Marking as read", ids) { markRead(it) }
    suspend fun bulkMute(ids: List<String>, muted: Boolean) =
        runBulk(if (muted) "Muting chats" else "Unmuting chats", ids) { m.setMuted(it, muted) }

    /** Runs [action] for every room, waiting out rate limits, with a progress dialog. Returns failures. */
    suspend fun runBulk(title: String, ids: List<String>, action: suspend (String) -> Unit): Int {
        var failed = 0
        _progress.value = BulkProgress(title, 0, ids.size)
        ids.forEachIndexed { i, id ->
            try {
                retryOnRateLimit {
                    try { action(id) } catch (e: Exception) {
                        _progress.value = BulkProgress(title, i, ids.size, if (e.message?.contains("LIMIT") == true) "Waiting for the server…" else null)
                        throw e
                    }
                }
            } catch (_: Exception) {
                failed++
            }
            _progress.value = BulkProgress(title, i + 1, ids.size)
        }
        _progress.value = null
        m.refreshRooms(ids)
        return failed
    }
}
