package app.switchboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.switchboard.R
import app.switchboard.core.AuthState
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.ui.chat.ChatScreen
import app.switchboard.ui.chat.MembersScreen
import app.switchboard.ui.chat.RoomDetailsScreen
import app.switchboard.ui.chat.SpaceMediaScreen
import app.switchboard.ui.chat.UserProfileScreen
import app.switchboard.ui.home.HomeScreen
import app.switchboard.ui.home.LocalRoomUi
import app.switchboard.ui.home.RoomUi
import app.switchboard.ui.home.RoomUiHost
import app.switchboard.ui.settings.EditRoomScreen
import app.switchboard.ui.settings.InvitesScreen
import app.switchboard.ui.settings.JoinRoomScreen
import app.switchboard.ui.settings.NewChatScreen
import app.switchboard.ui.settings.NewGroupScreen
import app.switchboard.ui.settings.SettingsAboutScreen
import app.switchboard.ui.settings.SettingsChatScreen
import app.switchboard.ui.settings.SettingsDevicesScreen
import app.switchboard.ui.settings.SettingsIgnoredScreen
import app.switchboard.ui.settings.SettingsNotificationsScreen
import app.switchboard.ui.settings.SettingsScreen
import app.switchboard.ui.settings.SettingsSecurityScreen
import app.switchboard.ui.settings.SettingsStyleScreen
import app.switchboard.ui.settings.ShareScreen
import app.switchboard.ui.settings.SpaceRoomsScreen
import app.switchboard.ui.settings.VerifyScreen
import app.switchboard.ui.theme.SwitchboardTheme
import app.switchboard.ui.theme.Sbt

object AppState {
    val nav = Nav().also { it.selectedSpace.value = Prefs.startSpace ?: Prefs.lastSpace }
    val roomUi = RoomUi()
}

@Composable
fun SwitchboardApp() {
    SwitchboardTheme {
        val auth by Sb.matrix.auth.collectAsState()
        when (auth) {
            AuthState.Loading -> Splash()
            AuthState.LoggedOut -> LoginScreen()
            is AuthState.LoggedIn -> MainScreens(AppState.nav, AppState.roomUi)
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Sbt.c.background), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(140.dp))
    }
}

@Composable
private fun MainScreens(nav: Nav, ui: RoomUi) {
    val c = Sbt.c
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { nav.messages.collect { snackbar.showSnackbar(it) } }
    CompositionLocalProvider(LocalNav provides nav, LocalRoomUi provides ui) {
        Box(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
            // Home stays underneath so it keeps its place while a chat is open.
            HomeScreen()
            val screen = nav.current
            BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
            if (screen != Screen.Home) {
                Box(Modifier.fillMaxSize().background(c.background)) { ScreenContent(screen) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        }
        RoomUiHost(ui)
    }
}

@Composable
private fun ScreenContent(s: Screen) {
    when (s) {
        Screen.Home -> {}
        is Screen.Chat -> ChatScreen(s.roomId, s.eventId)
        is Screen.RoomDetails -> RoomDetailsScreen(s.roomId)
        is Screen.Members -> MembersScreen(s.roomId)
        is Screen.SpaceMedia -> SpaceMediaScreen(s.spaceId)
        is Screen.UserProfile -> UserProfileScreen(s.userId, s.roomId)
        Screen.Settings -> SettingsScreen()
        Screen.SettingsStyle -> SettingsStyleScreen()
        Screen.SettingsChat -> SettingsChatScreen()
        Screen.SettingsNotifications -> SettingsNotificationsScreen()
        Screen.SettingsSecurity -> SettingsSecurityScreen()
        Screen.SettingsDevices -> SettingsDevicesScreen()
        Screen.SettingsIgnored -> SettingsIgnoredScreen()
        Screen.SettingsAbout -> SettingsAboutScreen()
        Screen.Verify -> VerifyScreen()
        Screen.NewChat -> NewChatScreen()
        is Screen.NewGroup -> NewGroupScreen(s.space, s.parentSpaceId)
        Screen.JoinRoom -> JoinRoomScreen()
        is Screen.Invites -> InvitesScreen(s.spaceId)
        is Screen.Share -> ShareScreen(s.text, s.uris)
        is Screen.EditRoom -> EditRoomScreen(s.roomId)
        is Screen.SpaceRooms -> SpaceRoomsScreen(s.spaceId)
    }
}
