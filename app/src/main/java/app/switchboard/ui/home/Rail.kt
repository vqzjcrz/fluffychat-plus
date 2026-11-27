package app.switchboard.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.switchboard.core.RoomSummary
import app.switchboard.ui.common.Avatar
import app.switchboard.ui.common.CountBadge
import app.switchboard.ui.theme.Sbt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val ItemSize = 50.dp

@Composable
fun Rail(
    spaces: List<RoomSummary>,
    badges: Map<String, HomeData.SpaceBadge>,
    allBadge: HomeData.SpaceBadge,
    selected: String?,
    profileUrl: String?,
    profileName: String,
    onSelect: (String?) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sbt.c
    var list by remember { mutableStateOf(spaces) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(spaces) { if (!dragging) list = spaces }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = list.indexOfFirst { it.id == from.key }
        val toIdx = list.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) list = list.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
    }
    Box(modifier.width(72.dp).fillMaxHeight().background(c.background)) {
        Column(Modifier.fillMaxHeight()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "__all") {
                    RailSlot(selected = selected == null, badge = allBadge, onClick = { onSelect(null) }) {
                        Box(
                            Modifier.size(ItemSize).clip(CircleShape).background(if (selected == null) c.raised else c.surface)
                                .border(1.dp, c.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Chat, "All chats", tint = if (selected == null) c.text else c.textMuted, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                items(list, key = { it.id }) { space ->
                    ReorderableItem(reorder, key = space.id) { isDragging ->
                        val isSel = selected == space.id
                        Box(
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { dragging = true },
                                onDragStopped = { dragging = false; onReorder(list.map { it.id }) },
                            ),
                        ) {
                            RailSlot(selected = isSel, badge = badges[space.id], onClick = { onSelect(space.id) }, lifted = isDragging) {
                                Avatar(space.avatarUrl, space.realName, size = ItemSize, key = space.id, grayscale = !isSel && !isDragging)
                            }
                        }
                    }
                }
                item(key = "__add") {
                    Box(Modifier.padding(vertical = 6.dp).size(ItemSize).clip(CircleShape).clickable(onClick = onAdd).drawBehind {
                        drawCircle(
                            color = c.textFaint, radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                        )
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, "Add a space", tint = c.textMuted)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.clickable(onClick = onProfile)) {
                    Avatar(profileUrl, profileName, size = 44.dp)
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(18.dp).clip(CircleShape)
                            .background(c.background).padding(2.dp).clip(CircleShape).background(c.raised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Settings, "Settings", tint = c.text, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
        VerticalDivider(Modifier.align(Alignment.CenterEnd), thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun RailSlot(
    selected: Boolean,
    badge: HomeData.SpaceBadge?,
    onClick: () -> Unit,
    lifted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Sbt.c
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(36.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(c.accent))
        }
        Box(
            Modifier.size(ItemSize + 8.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                .then(if (lifted) Modifier.background(c.raised) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (badge != null && (badge.count > 0 || badge.mention)) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) {
                CountBadge(badge.count, badge.mention && badge.count == 0L)
            }
        } else if (badge != null && badge.unread && !selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 4.dp).size(10.dp).clip(CircleShape).background(c.accent))
        }
    }
}
