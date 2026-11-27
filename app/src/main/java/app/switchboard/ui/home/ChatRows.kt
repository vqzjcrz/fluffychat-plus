package app.switchboard.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Plus
import app.switchboard.core.RoomSummary
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.CountBadge
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt

/** A chat in a list: avatar, name, last message and when it was sent. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(
    s: RoomSummary,
    plus: Plus,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showSender: Boolean = true,
    typing: String? = null,
) {
    val c = Sbt.c
    val unread = s.isUnread || s.isInvite
    val readOnly = HomeData.isReadOnly(s, plus)
    val snoozed = plus.snoozedUntil(s.id) != null
    val name = plus.displayName(s)
    val bg = when {
        selected -> c.raised
        s.hasMention -> c.accent.copy(alpha = if (c.isLight) 0.10f else 0.08f)
        else -> Color.Transparent
    }
    Box(Modifier.fillMaxWidth().background(bg).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        if (s.hasMention) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(44.dp).background(c.accent))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(s.avatarUrl, name, size = 50.dp, key = s.id)
                if (selected) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = c.onAccent, modifier = Modifier.size(14.dp)) }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, fontFamily = Inter, fontSize = 16.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (unread) c.text else c.textMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    RowIcons(readOnly, s.isMuted, snoozed)
                }
                val preview = when {
                    s.isInvite -> "Invited by ${s.inviterName ?: "someone"}"
                    typing != null -> typing
                    else -> buildPreview(s, showSender)
                }
                if (preview.isNotEmpty()) {
                    Text(
                        preview, style = Sbt.small,
                        color = when { typing != null -> c.accent; unread -> c.textMuted; else -> c.textFaint },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(HomeData.timeAgo(s.lastTs), style = Sbt.tiny, color = if (unread) c.textMuted else c.textFaint)
                if (s.isInvite) CountBadge(0, false)
                else if (s.isUnread && (s.badgeCount > 0 || s.hasMention)) CountBadge(s.badgeCount, s.hasMention, s.isMuted)
                else if (s.markedUnread) CountBadge(0, false, s.isMuted)
            }
        }
    }
}

private fun buildPreview(s: RoomSummary, showSender: Boolean): String {
    val p = s.preview ?: return ""
    if (!showSender || s.previewSender == null) return p
    return when {
        s.previewIsOwn -> "You: $p"
        s.isDirect -> p
        else -> "${s.previewSender}: $p"
    }
}

@Composable
private fun RowIcons(readOnly: Boolean, muted: Boolean, snoozed: Boolean) {
    val c = Sbt.c
    if (readOnly) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Lock, "Read-only", tint = c.textFaint, modifier = Modifier.size(13.dp)) }
    if (muted) { Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.NotificationsOff, "Muted", tint = c.textFaint, modifier = Modifier.size(14.dp)) }
    if (snoozed) { Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.Bedtime, "Snoozed", tint = c.textFaint, modifier = Modifier.size(14.dp)) }
}

/** A Discord-style channel row: "# name". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    s: RoomSummary,
    plus: Plus,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = Sbt.c
    val unread = s.isUnread || s.isInvite
    val readOnly = HomeData.isReadOnly(s, plus)
    val name = plus.displayName(s).removePrefix("#")
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.raised else if (s.hasMention) c.accent.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                }
            } else if (readOnly) {
                Icon(Icons.Filled.Lock, null, tint = if (unread) c.text else c.textFaint, modifier = Modifier.size(18.dp))
            } else {
                Text("#", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (unread) c.text else c.textFaint, modifier = Modifier.width(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                name, fontFamily = Inter, fontSize = 17.sp,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                color = if (unread) c.text else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (plus.snoozedUntil(s.id) != null) Icon(Icons.Outlined.Bedtime, "Snoozed", tint = c.textFaint, modifier = Modifier.size(15.dp).padding(end = 2.dp))
            if (s.isMuted) Icon(Icons.Outlined.NotificationsOff, "Muted", tint = c.textFaint, modifier = Modifier.size(15.dp))
            if (readOnly && !unread) {
                Text("READ-ONLY", style = Sbt.kicker.copy(fontSize = 10.sp), color = c.textFaint, modifier = Modifier.padding(start = 8.dp))
            }
            if (s.isUnread && (s.badgeCount > 0 || s.hasMention)) {
                Spacer(Modifier.width(8.dp)); CountBadge(s.badgeCount, s.hasMention, s.isMuted)
            }
        }
    }
}

/**
 * Swipe actions: right marks read/unread, left pins/unpins (when [onPin] is
 * given). The row snaps back after the action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(
    unread: Boolean,
    pinned: Boolean,
    onToggleRead: () -> Unit,
    onPin: (() -> Unit)?,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) { content(); return }
    val c = Sbt.c
    val read by rememberUpdatedState(onToggleRead)
    val pin by rememberUpdatedState(onPin)
    val last = androidx.compose.runtime.remember { longArrayOf(0L) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            val now = System.currentTimeMillis()
            if (now - last[0] < 700) return@rememberSwipeToDismissBoxState false
            if (v != SwipeToDismissBoxValue.Settled) last[0] = now
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> read()
                SwipeToDismissBoxValue.EndToStart -> pin?.invoke()
                else -> {}
            }
            false
        },
        positionalThreshold = { it * 0.3f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = onPin != null,
        backgroundContent = {
            val dir = state.dismissDirection
            Box(
                Modifier.fillMaxSize().background(if (dir == SwipeToDismissBoxValue.EndToStart) c.raised else c.accent.copy(alpha = 0.85f)).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (unread) Icons.Outlined.MarkChatRead else Icons.Outlined.MarkChatUnread, null, tint = c.onAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(if (unread) "Mark read" else "Mark unread", color = c.onAccent, fontFamily = Inter, fontWeight = FontWeight.Bold)
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pinned) "Unpin" else "Pin", color = c.text, fontFamily = Inter, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, null, tint = c.text)
                    }
                }
            }
        },
    ) { Box(Modifier.background(c.background)) { content() } }
}
