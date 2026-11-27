package app.switchboard.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.switchboard.R
import app.switchboard.core.Sb
import app.switchboard.ui.common.Kicker
import app.switchboard.ui.theme.Sbt
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val c = Sbt.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ssoAvailable by remember { mutableStateOf(false) }

    fun serverFromUser(): String {
        if (server.isNotBlank()) return server.trim()
        val u = user.trim()
        return if (u.startsWith("@") && u.contains(":")) u.substringAfter(":") else ""
    }

    Column(
        Modifier.fillMaxSize().background(c.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(56.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp).background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(22.dp))
        Kicker("Matrix client")
        Text("Switchboard", style = Sbt.title, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your Matrix account. Your chats, spaces and bridges come back on their own.", style = Sbt.body, color = c.textMuted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = server, onValueChange = { server = it; ssoAvailable = false },
            label = { Text("Homeserver") }, placeholder = { Text("matrix.example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Username") }, placeholder = { Text("@you:example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = c.danger, style = Sbt.small)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            enabled = !busy && user.isNotBlank() && password.isNotBlank() && serverFromUser().isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        Sb.matrix.loginWithPassword(serverFromUser(), user, password)
                    } catch (e: Exception) {
                        error = friendlyLoginError(e)
                    }
                    busy = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = c.onAccent, strokeWidth = 2.dp) else Text("Sign in")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            enabled = !busy && serverFromUser().isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val info = Sb.matrix.checkServer(serverFromUser())
                        ssoAvailable = info.sso
                        if (!info.sso) {
                            error = "This server doesn't offer single sign-on. Use your username and password."
                        } else {
                            val url = Sb.matrix.startSso(serverFromUser())
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    } catch (e: Exception) {
                        error = friendlyLoginError(e)
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Sign in with single sign-on") }
        Spacer(Modifier.height(40.dp))
    }
}

fun friendlyLoginError(e: Exception): String {
    val m = e.message ?: e.toString()
    return when {
        m.contains("M_FORBIDDEN") || m.contains("Invalid username or password", true) -> "Wrong username or password."
        m.contains("sliding sync", true) || m.contains("SlidingSync", true) ->
            "This homeserver doesn't support the sync method Switchboard uses (Simplified Sliding Sync). Update Synapse to a recent version."
        m.contains("resolve", true) || m.contains("dns", true) || m.contains("connect", true) -> "Couldn't reach that homeserver. Check the address."
        else -> m.take(300)
    }
}
