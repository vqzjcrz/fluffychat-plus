package dev.hush.client.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hush.client.discord.ChannelKind
import dev.hush.client.discord.ChannelUi
import dev.hush.client.discord.ConnectionState
import dev.hush.client.discord.GuildUi
import dev.hush.client.discord.Unread
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.Tab
import dev.hush.client.ui.components.MentionBadge
import dev.hush.client.ui.components.RemoteIcon
import dev.hush.client.ui.components.UserAvatar
import dev.hush.client.ui.components.avatarShape
import dev.hush.client.ui.theme.LocalPalette

// Bottom-navigation shell: four tabs, chat opens as a full screen over it.
@Composable
fun HomeShell(vm: AppViewModel) {
    val p = LocalPalette.current
    val unread by vm.unread.collectAsStateWithLifecycle()
    val dms by vm.dms.collectAsStateWithLifecycle()
    val guilds by vm.guilds.collectAsStateWithLifecycle()

    val dmActivity = remember(dms, unread) { dms.any { unread[it.id]?.unread == true } }
    val mentionTotal = remember(unread) { unread.values.sumOf { it.mentions } }
    val serverActivity = remember(guilds, unread) {
        guilds.any { g -> !g.muted && g.channels.any { c -> !c.muted && unread[c.id]?.unread == true } }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.tab) {
                Tab.MESSAGES -> MessagesTab(vm)
                Tab.SERVERS -> ServersTab(vm)
                Tab.MENTIONS -> MentionsTab(vm)
                Tab.YOU -> YouTab(vm)
            }
        }
        BottomBar(
            tab = vm.tab,
            dmActivity = dmActivity,
            serverActivity = serverActivity,
            mentions = mentionTotal,
            onSelect = { vm.tab = it },
        )
    }
}

@Composable
private fun BottomBar(
    tab: Tab,
    dmActivity: Boolean,
    serverActivity: Boolean,
    mentions: Int,
    onSelect: (Tab) -> Unit,
) {
    val p = LocalPalette.current
    Column {
        HorizontalDivider(color = p.divider, thickness = 0.5.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.surface)
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Modifier.weight(1f), Icons.Default.Email, "Messages", tab == Tab.MESSAGES, dot = dmActivity) { onSelect(Tab.MESSAGES) }
            NavItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.List, "Servers", tab == Tab.SERVERS, dot = serverActivity) { onSelect(Tab.SERVERS) }
            NavItem(Modifier.weight(1f), Icons.Default.Notifications, "Mentions", tab == Tab.MENTIONS, count = mentions) { onSelect(Tab.MENTIONS) }
            NavItem(Modifier.weight(1f), Icons.Default.Person, "You", tab == Tab.YOU) { onSelect(Tab.YOU) }
        }
    }
}

@Composable
private fun NavItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    dot: Boolean = false,
    count: Int = 0,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val tint = if (selected) p.accent else p.muted
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            if (count > 0) {
                MentionBadge(count, Modifier.align(Alignment.TopEnd).padding(start = 14.dp, bottom = 12.dp))
            } else if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 12.dp, bottom = 12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(p.text),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TabHeader(title: String, trailing: @Composable (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}

// ---------------------------------------------------------------- Messages

@Composable
private fun MessagesTab(vm: AppViewModel) {
    val dms by vm.dms.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TabHeader("Messages")
        Box(Modifier.weight(1f)) {
            when {
                dms.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(dms, key = { it.id }) { dm -> DmRow(dm, unread[dm.id]) { vm.openChannel(dm.id) } }
                }
                connection != ConnectionState.CONNECTED -> Loading()
                else -> EmptyHint("No conversations yet.")
            }
        }
    }
}

@Composable
private fun DmRow(dm: ChannelUi, u: Unread?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val mentions = u?.mentions ?: 0
    val active = u?.unread == true || mentions > 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIcon(dm.iconUrl, dm.name, 46.dp, avatarShape())
        Spacer(Modifier.width(14.dp))
        Text(
            text = dm.name,
            color = if (active) p.text else p.muted,
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mentions > 0) MentionBadge(mentions) else if (active) UnreadDot()
    }
}

// ---------------------------------------------------------------- Servers

@Composable
private fun ServersTab(vm: AppViewModel) {
    val guilds by vm.guilds.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TabHeader("Servers")
        Box(Modifier.weight(1f)) {
            when {
                guilds.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(guilds, key = { it.id }) { g ->
                        ServerTile(g, guildHasUnread(g, unread)) { vm.openServer(g.id) }
                    }
                }
                connection != ConnectionState.CONNECTED -> Loading()
                else -> EmptyHint("You're not in any servers.")
            }
        }
    }
}

@Composable
private fun ServerTile(g: GuildUi, unread: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            RemoteIcon(g.iconUrl, g.name, 64.dp, RoundedCornerShape(20.dp))
            if (unread && !g.muted) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape)
                        .background(p.background).padding(2.dp).clip(CircleShape).background(p.accent),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = g.name,
            color = p.muted,
            fontSize = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
    }
}

// ---------------------------------------------------------------- Mentions

@Composable
private fun MentionsTab(vm: AppViewModel) {
    val unread by vm.unread.collectAsStateWithLifecycle()
    val guilds by vm.guilds.collectAsStateWithLifecycle()
    val dms by vm.dms.collectAsStateWithLifecycle()
    val rows = remember(unread, guilds, dms) {
        unread.filterValues { it.mentions > 0 }.keys.mapNotNull { id -> vm.findChannel(id)?.let { it to (unread[id]?.mentions ?: 0) } }
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Mentions")
        Box(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                EmptyHint("No unread mentions. You're all caught up.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(rows, key = { it.first.id }) { (ch, count) -> MentionRow(ch, count) { vm.openChannel(ch.id) } }
                }
            }
        }
    }
}

@Composable
private fun MentionRow(ch: ChannelUi, count: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val isDm = ch.kind == ChannelKind.DM || ch.kind == ChannelKind.GROUP_DM
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDm) {
            RemoteIcon(ch.iconUrl, ch.name, 42.dp, avatarShape())
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(p.elevated), contentAlignment = Alignment.Center) {
                Text("#", color = p.muted, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = if (isDm) ch.name else "#${ch.name}",
            color = p.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MentionBadge(count)
    }
}

// ---------------------------------------------------------------- You

@Composable
private fun YouTab(vm: AppViewModel) {
    val p = LocalPalette.current
    val me by vm.me.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val (dot, label) = when (connection) {
        ConnectionState.CONNECTED -> p.success to "Online"
        ConnectionState.CONNECTING -> p.warning to "Connecting…"
        ConnectionState.DISCONNECTED -> p.muted to "Offline"
        ConnectionState.AUTH_FAILED -> p.danger to "Signed out"
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader("You")
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                UserAvatar(me, 96.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape)
                        .background(p.background).padding(4.dp).clip(CircleShape).background(dot),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(me?.displayName ?: "…", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            me?.username?.let { Text("@$it", color = p.muted, fontSize = 14.sp) }
            Text(label, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        YouRow("Settings", "Themes, layout, notifications, privacy") { vm.openSettings() }
        HorizontalDivider(color = p.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        YouRow("Log out", "Sign out of this account", danger = true) { vm.logout() }
    }
}

@Composable
private fun YouRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, color = if (danger) p.danger else p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = p.muted, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Server channels screen

@Composable
fun ServerChannelsScreen(vm: AppViewModel, guildId: String) {
    val p = LocalPalette.current
    val guilds by vm.guilds.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val guild = remember(guilds, guildId) { guilds.firstOrNull { it.id == guildId } }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
                }
                RemoteIcon(guild?.iconUrl, guild?.name ?: "?", 30.dp, RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    guild?.name ?: "Server",
                    color = p.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = p.divider, thickness = 0.5.dp)
        }
        if (guild == null) {
            Loading()
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(guild.channels, key = { it.id }) { ch ->
                    if (ch.kind == ChannelKind.CATEGORY) CategoryHeader(ch.name)
                    else ChannelRow(ch, unread[ch.id]) { vm.openChannel(ch.id) }
                }
            }
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (ch.kind == ChannelKind.ANNOUNCEMENT) "»" else "#",
            color = p.muted.copy(alpha = if (ch.muted) 0.5f else 1f),
            fontSize = 20.sp,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = ch.name,
            color = when {
                active -> p.text
                ch.muted -> p.muted.copy(alpha = 0.55f)
                else -> p.muted
            },
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mentions > 0) MentionBadge(mentions) else if (active) UnreadDot()
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun UnreadDot() {
    val p = LocalPalette.current
    Box(Modifier.size(9.dp).clip(CircleShape).background(p.text))
}

@Composable
private fun Loading() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), color = p.accent, strokeWidth = 2.5.dp)
    }
}

private fun guildHasUnread(g: GuildUi, unread: Map<String, Unread>): Boolean =
    g.channels.any { c -> !c.muted && unread[c.id]?.unread == true }
