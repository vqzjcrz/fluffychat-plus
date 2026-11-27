package app.switchboard.ui.settings
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
object JoinRoomState { val pending = MutableStateFlow<String?>(null) }
@Composable fun EditRoomScreen(roomId: String) {}
@Composable fun InvitesScreen(spaceId: String?) {}
@Composable fun JoinRoomScreen() {}
@Composable fun NewChatScreen() {}
@Composable fun NewGroupScreen(space: Boolean, parent: String?) {}
@Composable fun SettingsScreen() {}
@Composable fun SettingsStyleScreen() {}
@Composable fun SettingsChatScreen() {}
@Composable fun SettingsNotificationsScreen() {}
@Composable fun SettingsSecurityScreen() {}
@Composable fun SettingsDevicesScreen() {}
@Composable fun SettingsIgnoredScreen() {}
@Composable fun SettingsAboutScreen() {}
@Composable fun ShareScreen(text: String?, uris: List<android.net.Uri>) {}
@Composable fun SpaceRoomsScreen(spaceId: String) {}
@Composable fun VerifyScreen() {}
