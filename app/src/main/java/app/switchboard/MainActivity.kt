package app.switchboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.switchboard.core.MatrixManager
import app.switchboard.core.Sb
import app.switchboard.ui.AppState
import app.switchboard.ui.Screen
import app.switchboard.ui.SwitchboardApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SwitchboardApp() }
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val nav = AppState.nav
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null && data.toString().startsWith(MatrixManager.SSO_REDIRECT)) {
            Sb.matrix.scope.launch {
                runCatching { Sb.matrix.finishSso(data.toString()) }
                    .onFailure { nav.toast("Sign-in failed: ${it.message}") }
            }
            return
        }
        intent.getStringExtra(EXTRA_ROOM_ID)?.let { roomId ->
            nav.openChat(roomId, intent.getStringExtra(EXTRA_EVENT_ID))
            intent.removeExtra(EXTRA_ROOM_ID)
            return
        }
        if (intent.action == Intent.ACTION_VIEW && data != null && (data.host == "matrix.to" || data.scheme == "matrix")) {
            nav.push(Screen.JoinRoom)
            app.switchboard.ui.settings.JoinRoomState.pending.value = data.toString()
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uri = streamExtra(intent)
                nav.push(Screen.Share(text, listOfNotNull(uri)))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                nav.push(Screen.Share(intent.getStringExtra(Intent.EXTRA_TEXT), uris ?: emptyList()))
            }
        }
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
