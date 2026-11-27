package app.switchboard.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.switchboard.core.PlusEdit
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.common.ActionSheet
import app.switchboard.ui.common.SheetAction
import app.switchboard.ui.theme.SwitchboardTheme
import app.switchboard.ui.theme.currentColors
import app.switchboard.ui.theme.withAccent
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val ui = LocalRoomUi.current
    val scope = rememberCoroutineScope()
    val summaries by Sb.matrix.summaries.collectAsState()
    val order by Sb.matrix.order.collectAsState()
    val children by Sb.matrix.spaceChildren.collectAsState()
    val plus by Sb.matrix.accountData.state.collectAsState()
    val profile by Sb.matrix.profile.collectAsState()
    val loaded by Sb.matrix.loaded.collectAsState()
    var selected by nav.selectedSpace
    var addMenu by remember { mutableStateOf(false) }

    val spaces = remember(summaries, order, children, plus) { HomeData.topSpaces(summaries, order, children, plus) }
    val badges = remember(spaces, summaries, children) { spaces.associate { it.id to HomeData.spaceBadge(it.id, summaries, children) } }
    val allBadge = remember(summaries, order) { HomeData.totalBadge(HomeData.allChats(summaries, order)) }

    // A space that was left or doesn't exist anymore falls back to all chats.
    LaunchedEffect(selected, loaded, summaries.keys) {
        val s = selected
        if (loaded && s != null && summaries[s]?.isSpace != true) selected = null
    }
    BackHandler(enabled = ui.selecting) { ui.endSelection() }
    BackHandler(enabled = !ui.selecting && selected != null) { selected = null }

    val base = currentColors()
    val spaceColor = selected?.let { plus.spaceColor(it) }
    SwitchboardTheme(colors = base.withAccent(spaceColor?.let { Color(it) })) {
        Row(Modifier.fillMaxSize().background(base.background)) {
            Rail(
                spaces = spaces,
                badges = badges,
                allBadge = allBadge,
                selected = selected,
                profileUrl = profile?.avatarUrl,
                profileName = profile?.displayName ?: Sb.matrix.userId ?: "?",
                onSelect = { id ->
                    ui.endSelection()
                    selected = id
                    Prefs.lastSpace = id
                },
                onReorder = { ids -> scope.launch { PlusEdit.setSpaceOrder(ids) } },
                onAdd = { addMenu = true },
                onProfile = { nav.push(Screen.Settings) },
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val s = selected
                if (s == null) ChatListPane() else SpaceView(s)
            }
        }
    }

    if (addMenu) {
        ActionSheet(
            title = null,
            actions = listOf(
                SheetAction("Create a space", Icons.Outlined.AddCircleOutline) { nav.push(Screen.NewGroup(space = true)) },
                SheetAction("New group chat", Icons.Outlined.GroupAdd) { nav.push(Screen.NewGroup(space = false, parentSpaceId = selected)) },
                SheetAction("Join a room or space", Icons.Outlined.TravelExplore) { nav.push(Screen.JoinRoom) },
            ),
            onDismiss = { addMenu = false },
        )
    }
}
