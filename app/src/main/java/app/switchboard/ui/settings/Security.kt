package app.switchboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.core.Sb
import app.switchboard.ui.LocalNav
import app.switchboard.ui.Screen
import app.switchboard.ui.chat.copyText
import app.switchboard.ui.common.ConfirmDialog
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.common.ListRow
import app.switchboard.ui.common.ScreenHeader
import app.switchboard.ui.common.TextInputDialog
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails
import org.matrix.rustcomponents.sdk.VerificationState

@Composable
fun SettingsSecurityScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val recovery by Sb.matrix.recovery.collectAsState()
    val verification by Sb.matrix.verification.collectAsState()
    var enterKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val enc = remember { Sb.matrix.client?.encryption() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Security and encryption", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ListRow(
                "This device",
                when (verification) { VerificationState.VERIFIED -> "Verified"; VerificationState.UNVERIFIED -> "Not verified yet"; else -> "Checking…" },
                Icons.Outlined.VerifiedUser,
            )
            ListRow(
                "Key backup",
                when (recovery) {
                    RecoveryState.ENABLED -> "On: a recovery key protects your message keys"
                    RecoveryState.DISABLED -> "Off"
                    RecoveryState.INCOMPLETE -> "Set up on another device; enter your recovery key here"
                    else -> "Checking…"
                },
                Icons.Outlined.Key,
            )
            if (busy) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = c.accent) }
            if (recovery == RecoveryState.INCOMPLETE || recovery == RecoveryState.ENABLED || verification == VerificationState.UNVERIFIED) {
                ListRow("Enter recovery key", "Unlocks your encrypted messages on this device", Icons.Outlined.Key) { enterKey = true }
            }
            ListRow("Verify with another device", "Compare emoji with a logged-in session", Icons.Outlined.Devices) { nav.push(Screen.Verify) }
            if (recovery == RecoveryState.DISABLED) {
                ListRow("Set up recovery", "Creates a recovery key for your backups", Icons.Outlined.LockReset) {
                    busy = true
                    scope.launch {
                        try {
                            newKey = enc?.enableRecovery(false, null, object : EnableRecoveryProgressListener {
                                override fun onUpdate(status: EnableRecoveryProgress) {}
                            })
                        } catch (e: Exception) { nav.toast("Couldn't set up recovery: ${e.message}") }
                        busy = false
                    }
                }
            }
            if (recovery == RecoveryState.ENABLED) {
                ListRow("Change recovery key", "Makes a new key; the old one stops working", Icons.Outlined.LockReset) { confirmReset = true }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Your account, chats, spaces and settings are on your homeserver and come back on their own. " +
                    "Encrypted messages need your recovery key or another verified device.",
                style = Sbt.small, color = c.textMuted, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
    if (enterKey) {
        TextInputDialog("Recovery key", hint = "EsT… or your passphrase", confirm = "Unlock", onDismiss = { enterKey = false }) { key ->
            busy = true
            scope.launch {
                runCatching { enc?.recover(key.trim()) }
                    .onSuccess { nav.toast("Unlocked. Older messages decrypt as they load.") }
                    .onFailure { nav.toast("That key didn't work: ${it.message}") }
                busy = false
            }
        }
    }
    if (confirmReset) {
        ConfirmDialog("Change recovery key?", "You'll get a new key. Save it somewhere safe.", "Change", onDismiss = { confirmReset = false }) {
            busy = true
            scope.launch {
                runCatching { newKey = enc?.resetRecoveryKey() }.onFailure { nav.toast("Couldn't change it: ${it.message}") }
                busy = false
            }
        }
    }
    newKey?.let { key ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Your recovery key") },
            text = {
                Column {
                    Text("Save this somewhere safe, like a password manager. You need it to read encrypted messages after reinstalling.", style = Sbt.small)
                    Spacer(Modifier.height(12.dp))
                    Text(key, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = c.text)
                }
            },
            confirmButton = { TextButton(onClick = { copyText(context, key); nav.toast("Copied") }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { newKey = null }) { Text("I saved it") } },
        )
    }
}

/** Emoji verification with another of the user's devices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerifyScreen() {
    val c = Sbt.c
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<SessionVerificationController?>(null) }
    var step by remember { mutableStateOf("start") }
    var emojis by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var incoming by remember { mutableStateOf<SessionVerificationRequestDetails?>(null) }

    DisposableEffect(Unit) {
        scope.launch {
            try {
                val ctl = Sb.matrix.client!!.getSessionVerificationController()
                ctl.setDelegate(object : SessionVerificationControllerDelegate {
                    override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) { incoming = details; step = "incoming" }
                    override fun didAcceptVerificationRequest() {
                        step = "starting"
                        scope.launch { runCatching { ctl.startSasVerification() } }
                    }
                    override fun didStartSasVerification() { step = "waiting_emoji" }
                    override fun didReceiveVerificationData(data: SessionVerificationData) {
                        if (data is SessionVerificationData.Emojis) {
                            emojis = data.emojis.map { it.symbol() to it.description() }
                            step = "compare"
                        }
                    }
                    override fun didFail() { step = "failed" }
                    override fun didCancel() { step = "cancelled" }
                    override fun didFinish() { step = "done" }
                })
                controller = ctl
            } catch (e: Exception) { step = "failed" }
        }
        onDispose { runCatching { controller?.setDelegate(null) } }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("Verify this device", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                "start" -> {
                    Text("Open Switchboard, Element or another Matrix app where you're already logged in. You'll compare a few emoji on both screens.", style = Sbt.body, color = c.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        enabled = controller != null,
                        onClick = { step = "requested"; scope.launch { runCatching { controller?.requestDeviceVerification() }.onFailure { step = "failed" } } },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text("Start verification") }
                }
                "requested", "starting", "waiting_emoji" -> {
                    CircularProgressIndicator(color = c.accent)
                    Spacer(Modifier.height(16.dp))
                    Text(if (step == "requested") "Accept the request on your other device." else "Getting the emoji…", style = Sbt.body, color = c.textMuted, textAlign = TextAlign.Center)
                }
                "incoming" -> {
                    Text("${incoming?.deviceDisplayName ?: incoming?.deviceId ?: "Another device"} wants to verify this device.", style = Sbt.body, color = c.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        step = "starting"
                        scope.launch { runCatching { controller?.acceptVerificationRequest() } }
                    }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) { Text("Accept") }
                }
                "compare" -> {
                    Kicker("Do these match?")
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        emojis.forEach { (e, d) ->
                            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(e, fontSize = 38.sp)
                                Text(d, style = Sbt.tiny, color = c.textMuted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { scope.launch { runCatching { controller?.approveVerification() } } },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent), modifier = Modifier.fillMaxWidth()) { Text("They match") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { scope.launch { runCatching { controller?.declineVerification() } } }, modifier = Modifier.fillMaxWidth()) { Text("They don't match", color = c.danger) }
                }
                "done" -> {
                    Text("Verified", fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 28.sp, color = c.accent)
                    Spacer(Modifier.height(8.dp))
                    Text("This device can read your encrypted messages now.", style = Sbt.body, color = c.textMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { nav.pop() }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) { Text("Done") }
                }
                else -> {
                    Text(if (step == "cancelled") "Verification was cancelled." else "Verification didn't work.", style = Sbt.body, color = c.text)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { step = "start" }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) { Text("Try again") }
                }
            }
        }
    }
}
