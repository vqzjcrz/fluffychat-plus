package app.switchboard.ui.home

import app.switchboard.core.Bridges
import app.switchboard.core.Plus
import app.switchboard.core.RoomSummary
import app.switchboard.core.SortMode
import app.switchboard.core.SpaceChildRef
import org.matrix.rustcomponents.sdk.Membership

/** Pure helpers that turn the room list into what the home screens show. */
object HomeData {
    /** Joined spaces that aren't inside another joined space, in the user's order. */
    fun topSpaces(
        summaries: Map<String, RoomSummary>,
        order: List<String>,
        children: Map<String, List<SpaceChildRef>>,
        plus: Plus,
    ): List<RoomSummary> {
        val spaces = order.mapNotNull { summaries[it] }.filter { it.isSpace && it.membership == Membership.JOINED }
        val nested = HashSet<String>()
        spaces.forEach { s -> children[s.id]?.forEach { nested.add(it.roomId) } }
        val top = spaces.filter { it.id !in nested }
        return sortByOrder(top, plus.spaceOrder()) { it.id }
    }

    fun <T> sortByOrder(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val pos = order.withIndex().associate { it.value to it.index }
        val (known, rest) = items.partition { id(it) in pos }
        return known.sortedBy { pos[id(it)] } + rest
    }

    /** All rooms (not spaces) shown in the Chats tab, invites first. */
    fun allChats(summaries: Map<String, RoomSummary>, order: List<String>): List<RoomSummary> {
        val rooms = order.mapNotNull { summaries[it] }.filter { !it.isSpace }
        val (invites, joined) = rooms.partition { it.isInvite }
        return invites + joined
    }

    /** IDs of every room in a space, including rooms in its sub-spaces. */
    fun descendants(spaceId: String, children: Map<String, List<SpaceChildRef>>, depth: Int = 0, seen: MutableSet<String> = HashSet()): Set<String> {
        if (depth > 6 || !seen.add(spaceId)) return emptySet()
        val out = LinkedHashSet<String>()
        children[spaceId]?.forEach { c ->
            out.add(c.roomId)
            if (children.containsKey(c.roomId)) out.addAll(descendants(c.roomId, children, depth + 1, seen))
        }
        return out
    }

    data class SpaceBadge(val count: Long, val mention: Boolean, val unread: Boolean)

    fun spaceBadge(spaceId: String, summaries: Map<String, RoomSummary>, children: Map<String, List<SpaceChildRef>>): SpaceBadge {
        var count = 0L; var mention = false; var unread = false
        for (id in descendants(spaceId, children)) {
            val s = summaries[id] ?: continue
            if (s.isSpace) continue
            if (s.isInvite) { unread = true; count++; continue }
            if (s.hasMention) mention = true
            if (!s.isMuted) {
                count += s.notificationCount
                if (s.isUnread) unread = true
            }
        }
        return SpaceBadge(count, mention, unread)
    }

    fun totalBadge(chats: List<RoomSummary>): SpaceBadge {
        var count = 0L; var mention = false; var unread = false
        for (s in chats) {
            if (s.isInvite) { count++; unread = true; continue }
            if (s.hasMention) mention = true
            if (!s.isMuted) { count += s.notificationCount; if (s.isUnread) unread = true }
        }
        return SpaceBadge(count, mention, unread)
    }

    /** Sorts rooms by the space's sort mode. [recency] is the SDK order. */
    fun sortRooms(rooms: List<RoomSummary>, mode: SortMode, custom: List<String>, recency: Map<String, Int>): List<RoomSummary> {
        val byRecent = rooms.sortedBy { recency[it.id] ?: Int.MAX_VALUE }
        return when (mode) {
            SortMode.RECENT -> byRecent
            SortMode.UNREAD -> byRecent.sortedBy {
                when {
                    it.isInvite -> 0
                    it.hasMention -> 1
                    it.isUnread && !it.isMuted -> 2
                    else -> 3
                }
            }
            SortMode.CUSTOM -> sortByOrder(byRecent, custom) { it.id }
        }
    }

    /** One block of a space: a category (or the uncategorized chats). */
    data class Block(val categoryId: String?, val name: String?, val collapsed: Boolean, val rooms: List<RoomSummary>)

    data class SpaceLayout(
        val subspaces: List<RoomSummary>,
        val invites: List<RoomSummary>,
        val pinned: List<RoomSummary>,
        val blocks: List<Block>,
        val muted: List<RoomSummary>,
        val all: List<RoomSummary>,
    )

    fun spaceLayout(
        spaceId: String,
        summaries: Map<String, RoomSummary>,
        order: List<String>,
        children: Map<String, List<SpaceChildRef>>,
        plus: Plus,
        query: String,
    ): SpaceLayout {
        val recency = order.withIndex().associate { it.value to it.index }
        val childIds = children[spaceId]?.map { it.roomId } ?: emptyList()
        val members = childIds.mapNotNull { summaries[it] }
        val subspaces = sortByOrder(members.filter { it.isSpace && !it.isInvite }.sortedBy { recency[it.id] ?: Int.MAX_VALUE }, plus.spaceOrder()) { it.id }
        var rooms = members.filter { !it.isSpace }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            rooms = rooms.filter { plus.displayName(it).lowercase().contains(q) || it.realName.lowercase().contains(q) }
        }
        val invites = rooms.filter { it.isInvite }
        val joined = rooms.filter { !it.isInvite }
        val mode = plus.sortMode(spaceId)
        val custom = plus.childOrder(spaceId)
        val pinIds = plus.pins(spaceId)
        val pinned = pinIds.mapNotNull { id -> joined.firstOrNull { it.id == id } }
        val pinnedSet = pinned.map { it.id }.toSet()
        val rest = joined.filter { it.id !in pinnedSet }
        // While searching, muted chats show with the others.
        val (muted, active) = if (query.isNotBlank()) emptyList<RoomSummary>() to rest else rest.partition { it.isMuted }
        val catOf = plus.categoryOf(spaceId)
        val cats = plus.categories(spaceId).associateBy { it.id }
        val blocks = plus.blockOrder(spaceId).map { cid ->
            val inBlock = if (cid == null) active.filter { catOf[it.id] == null || cats[catOf[it.id]] == null }
            else active.filter { catOf[it.id] == cid }
            val cat = cid?.let { cats[it] }
            Block(cid, cat?.name, cat?.collapsed ?: false, sortRooms(inBlock, mode, custom, recency))
        }
        val all = sortRooms(joined, mode, custom, recency)
        return SpaceLayout(subspaces, invites, pinned, blocks, sortRooms(muted, mode, custom, recency), all)
    }

    /** Whether a chat should show as Discord-style channel (# name) in a space. */
    fun isChannel(s: RoomSummary): Boolean = !s.isDirect && Bridges.isDiscord(s.id)

    /** Read-only: no permission to post, else the user's choice, else Discord chats are read-only. */
    fun isReadOnly(s: RoomSummary, plus: Plus): Boolean {
        if (!s.canSendMessages) return true
        return plus.readOnlyOverride(s.id) ?: Bridges.isDiscord(s.id)
    }

    fun bridgeName(s: RoomSummary): String? = Bridges.get(s.id)?.name ?: Bridges.guess(s.heroIds)

    fun timeAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
        if (ts <= 0) return ""
        val d = (now - ts) / 1000
        return when {
            d < 60 -> "now"
            d < 3600 -> "${d / 60}m"
            d < 86400 -> "${d / 3600}h"
            d < 7 * 86400 -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(ts))
            else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }
    }
}
