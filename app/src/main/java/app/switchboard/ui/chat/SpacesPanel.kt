package app.switchboard.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Sb
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.CountBadge
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.home.HomeData
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt

/**
 * Discord-style panel that slides in over a chat: your spaces on the left,
 * the chats of one of them on the right.
 */
@Composable
fun SpacesPanel(currentRoom: String, startSpace: String?, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val c = Sbt.c
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val children by Sb.matrix.spaceChildren.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    var space by remember { mutableStateOf(startSpace) }
    val spaces = remember(summaries, order, children, plus) { HomeData.topSpaces(summaries, order, children, plus) }
    val rooms = remember(space, summaries, order, children, plus) {
        val s = space
        if (s == null) HomeData.allChats(summaries, order).filter { !it.isInvite }
        else HomeData.spaceLayout(s, summaries, order, children, plus, "").let { l -> l.pinned + l.blocks.flatMap { it.rooms } + l.muted }
    }
    Row(Modifier.fillMaxHeight().width(340.dp).background(c.background)) {
        LazyColumn(Modifier.width(68.dp).fillMaxHeight().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Box(
                    Modifier.padding(vertical = 5.dp).size(46.dp).clip(CircleShape).background(if (space == null) c.raised else c.surface)
                        .then(if (space == null) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                        .clickable { space = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.Chat, "All chats", tint = c.text, modifier = Modifier.size(20.dp)) }
            }
            items(spaces, key = { it.id }) { s ->
                val sel = s.id == space
                Box(
                    Modifier.padding(vertical = 5.dp).clip(CircleShape)
                        .then(if (sel) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                        .clickable { space = s.id }.padding(3.dp),
                ) { Avatar(s.avatarUrl, s.realName, size = 42.dp, key = s.id, grayscale = !sel) }
            }
        }
        VerticalDivider(thickness = 1.dp, color = c.line)
        Column(Modifier.fillMaxSize()) {
            val title = space?.let { summaries[it]?.realName } ?: "Chats"
            Column(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)) {
                Kicker(if (space == null) "Switchboard" else "Space")
                Text(title, fontFamily = app.switchboard.ui.theme.InterDisplay, fontWeight = FontWeight.Black, fontSize = 22.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rooms, key = { it.id }) { s ->
                    val active = s.id == currentRoom
                    val unread = s.isUnread
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) c.raised else Color.Transparent)
                            .clickable { onOpen(s.id) }.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (HomeData.isChannel(s)) {
                            if (HomeData.isReadOnly(s, plus)) Icon(Icons.Filled.Lock, null, tint = c.textFaint, modifier = Modifier.size(16.dp).width(22.dp))
                            else Text("#", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (unread) c.text else c.textFaint, modifier = Modifier.width(18.dp))
                        } else Avatar(s.avatarUrl, plus.displayName(s), size = 26.dp, key = s.id)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            plus.displayName(s).removePrefix("#"), fontFamily = Inter, fontSize = 15.sp,
                            fontWeight = if (unread || active) FontWeight.Bold else FontWeight.Medium,
                            color = if (unread || active) c.text else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (unread && (s.badgeCount > 0 || s.hasMention)) CountBadge(s.badgeCount, s.hasMention, s.isMuted)
                    }
                }
            }
        }
    }
}
