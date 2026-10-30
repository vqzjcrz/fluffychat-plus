package dev.hush.client.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hush.client.discord.ChannelKind
import dev.hush.client.discord.ChannelUi
import dev.hush.client.discord.ConnectionState
import dev.hush.client.discord.GuildUi
import dev.hush.client.discord.Unread
import dev.hush.client.discord.User
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.Route
import dev.hush.client.ui.components.MentionBadge
import dev.hush.client.ui.components.RemoteIcon
import dev.hush.client.ui.components.UserAvatar
import dev.hush.client.ui.components.avatarShape
import dev.hush.client.ui.theme.LocalPalette

private fun guildBadge(g: GuildUi, unread: Map<String, Unread>): Unread {
    var any = false
    var mentions = 0
    for (c in g.channels) {
        val u = unread[c.id] ?: continue
        if (u.unread && !c.muted) any = true
        mentions += u.mentions
    }
    return Unread(any && !g.muted, mentions)
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val guilds by vm.guilds.collectAsStateWithLifecycle()
    val dms by vm.dms.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val guild = guilds.firstOrNull { it.id == vm.selectedGuildId }

    Row(
        Modifier
            .fillMaxSize()
            .background(p.rail)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        GuildRail(
            guilds = guilds,
            dms = dms,
            unread = unread,
            selectedId = guild?.id,
            onSelect = { vm.selectedGuildId = it },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 18.dp))
                .background(p.surface),
        ) {
            Text(
                text = guild?.name ?: "Messages",
                color = p.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
            )
            HorizontalDivider(color = p.divider, thickness = 0.5.dp)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val empty = if (guild != null) guild.channels.isEmpty() else dms.isEmpty()
                if (empty) {
                    if (connection == ConnectionState.CONNECTED) {
                        Text(
                            text = if (guild != null) "No channels you can see." else "No conversations yet.",
                            color = p.muted,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.Center).size(26.dp),
                            color = p.accent,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (guild != null) {
                        items(guild.channels, key = { it.id }) { ch ->
                            if (ch.kind == ChannelKind.CATEGORY) {
                                CategoryHeader(ch.name)
                            } else {
                                ChannelRow(ch, unread[ch.id]) { vm.openChannel(ch.id) }
                            }
                        }
                    } else {
                        items(dms, key = { it.id }) { dm ->
                            DmRow(dm, unread[dm.id]) { vm.openChannel(dm.id) }
                        }
                    }
                }
            }

            UserPanel(me, connection) { vm.route = Route.Settings }
        }
    }
}

@Composable
private fun GuildRail(
    guilds: List<GuildUi>,
    dms: List<ChannelUi>,
    unread: Map<String, Unread>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val p = LocalPalette.current
    val dmMentions = remember(dms, unread) { dms.sumOf { unread[it.id]?.mentions ?: 0 } }
    LazyColumn(
        Modifier.width(72.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "home") {
            val selected = selectedId == null
            RailItem(selected = selected, hasUnread = false, mentions = dmMentions, onClick = { onSelect(null) }) {
                Box(
                    Modifier.fillMaxSize().background(if (selected) p.accent else p.elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Direct messages",
                        tint = if (selected) p.onAccent else p.text,
                    )
                }
            }
        }
        item(key = "divider") {
            Box(
                Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(p.divider),
            )
        }
        items(guilds, key = { it.id }) { g ->
            val badge = remember(g, unread) { guildBadge(g, unread) }
            RailItem(
                selected = g.id == selectedId,
                hasUnread = badge.unread,
                mentions = badge.mentions,
                onClick = { onSelect(g.id) },
            ) {
                RemoteIcon(g.iconUrl, g.name, 48.dp, RectangleShape)
            }
        }
    }
}

@Composable
private fun RailItem(
    selected: Boolean,
    hasUnread: Boolean,
    mentions: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val corner by animateDpAsState(if (selected) 16.dp else 24.dp, label = "corner")
    val pill by animateDpAsState(
        when {
            selected -> 36.dp
            hasUnread -> 8.dp
            else -> 0.dp
        },
        label = "pill",
    )
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(pill)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(p.text),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clip(RoundedCornerShape(corner))
                .clickable(onClick = onClick),
        ) {
            content()
        }
        if (mentions > 0) {
            MentionBadge(mentions, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp))
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    val p = LocalPalette.current
    Text(
        text = name.uppercase(),
        color = p.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChannelRow(ch: ChannelUi, u: Unread?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val mentions = u?.mentions ?: 0
    val active = (u?.unread == true && !ch.muted) || mentions > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (ch.kind == ChannelKind.ANNOUNCEMENT) "»" else "#",
            color = p.muted.copy(alpha = if (ch.muted) 0.5f else 1f),
            fontSize = 18.sp,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = ch.name,
            color = when {
                active -> p.text
                ch.muted -> p.muted.copy(alpha = 0.55f)
                else -> p.muted
            },
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mentions > 0) MentionBadge(mentions)
    }
}

@Composable
private fun DmRow(dm: ChannelUi, u: Unread?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val mentions = u?.mentions ?: 0
    val active = u?.unread == true || mentions > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIcon(dm.iconUrl, dm.name, 36.dp, avatarShape())
        Spacer(Modifier.width(12.dp))
        Text(
            text = dm.name,
            color = if (active) p.text else p.muted,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mentions > 0) MentionBadge(mentions)
    }
}

@Composable
private fun UserPanel(me: User?, connection: ConnectionState, onSettings: () -> Unit) {
    val p = LocalPalette.current
    val (dot, label) = when (connection) {
        ConnectionState.CONNECTED -> p.success to "Online"
        ConnectionState.CONNECTING -> p.warning to "Connecting…"
        ConnectionState.DISCONNECTED -> p.muted to "Offline"
        ConnectionState.AUTH_FAILED -> p.danger to "Signed out"
    }
    Column {
        HorizontalDivider(color = p.divider, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                UserAvatar(me, 36.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(p.surface)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = me?.displayName ?: "…",
                    color = p.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(label, color = p.muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = p.muted)
            }
        }
    }
}
