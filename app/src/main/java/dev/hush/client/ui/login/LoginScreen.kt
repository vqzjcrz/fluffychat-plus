package dev.hush.client.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.theme.LocalPalette

@Composable
fun LoginScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    var token by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(p.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("h", color = p.onAccent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Hush", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("A quiet, minimal Discord client.", color = p.muted, fontSize = 15.sp)
            }

            if (vm.sessionExpired) {
                Text("Your session ended. Sign in again to continue.", color = p.danger, fontSize = 14.sp)
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Account token") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.login(token) }),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(if (visible) "Hide" else "Show", color = p.muted, fontSize = 13.sp)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.divider,
                    focusedLabelColor = p.accent,
                    unfocusedLabelColor = p.muted,
                    cursorColor = p.accent,
                    focusedContainerColor = p.elevated,
                    unfocusedContainerColor = p.elevated,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { vm.login(token) },
                enabled = token.isNotBlank() && !vm.loggingIn,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = p.accent,
                    contentColor = p.onAccent,
                    disabledContainerColor = p.elevated,
                    disabledContentColor = p.muted,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (vm.loggingIn) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = p.onAccent, strokeWidth = 2.dp)
                } else {
                    Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            vm.loginError?.let { Text(it, color = p.danger, fontSize = 14.sp) }

            TextButton(onClick = { showHelp = !showHelp }) {
                Text(if (showHelp) "Hide instructions" else "How do I get my token?", color = p.accent)
            }

            AnimatedVisibility(visible = showHelp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val steps = listOf(
                        "On a computer, sign in at discord.com in Chrome, Edge or Firefox.",
                        "Press F12 to open Developer Tools and choose the Network tab.",
                        "Open any channel, then type \"messages\" into the network filter.",
                        "Select a request, and under Request Headers copy the value of \"authorization\".",
                        "Paste it here. It's stored encrypted on this phone and only ever sent to Discord.",
                    )
                    steps.forEachIndexed { i, step ->
                        Text("${i + 1}.  $step", color = p.text, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your token gives full access to your account, so never share it. " +
                    "Third-party clients aren't allowed by Discord's Terms of Service; use at your own risk.",
                color = p.muted,
                fontSize = 12.sp,
            )
        }
    }
}
