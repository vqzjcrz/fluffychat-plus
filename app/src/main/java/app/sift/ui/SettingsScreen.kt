@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.sift.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sift.BuildConfig
import app.sift.SiftViewModel

@Composable
fun SettingsScreen(vm: SiftViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasInternet = remember { ctx.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFile) }
    val bl = vm.blocklist

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                Section("Network access") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (hasInternet) "Allowed (blocklist sync only)" else "None",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        if (hasInternet)
                            "This is the self-hosted build. The only network request it ever makes is a GET for the blocklist file at the server URL below. Links you scan are never sent anywhere."
                        else
                            "Verified at runtime: Android reports this app does not hold the INTERNET permission, so it cannot open a connection to anything — not the links you scan, not a cloud service, nothing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Section("Blocklist") {
                    Text("%,d entries loaded".format(bl.total), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Matched against every scanned host (and its parent domains) and full URL. Accepts hosts files, " +
                            "one-domain-per-line lists, AdBlock '||domain^' rules, and full URLs — e.g. URLhaus, Phishing Army, " +
                            "OpenPhish, or your own IOC list.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Imported file: " + if (bl.importedCount > 0) "${bl.importedName ?: "file"} (%,d)".format(bl.importedCount) else "none",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy) {
                            Text(if (bl.importedCount > 0) "Replace file" else "Import file")
                        }
                        if (bl.importedCount > 0) TextButton(onClick = vm::clearImported) { Text("Remove") }
                    }
                }
            }

            if (BuildConfig.SELF_HOSTED) {
                item { ServerSection(vm) }
            }

            item {
                Section("What Sift checks") {
                    Text(
                        listOf(
                            "Fake hosts: user@host tricks, decimal/hex/octal IPs, raw IPs, local-network and localhost targets",
                            "Lookalikes: punycode/IDN homographs, mixed alphabets, typosquats and brand names on domains the brand doesn't own",
                            "Hosting: URL shorteners, IP loggers, tunnels & dynamic DNS, free site builders, IPFS, high-abuse TLDs, .zip/.mov domains",
                            "Hidden characters: right-to-left overrides, zero-width characters, tabs/newlines, double/needless encoding",
                            "Payloads: XSS, SQLi, command injection, path traversal, SSRF, Log4Shell, template injection, CRLF, PHP wrappers, deserialization",
                            "Downloads: APKs, executables, scripts, disk images, macro documents, double extensions",
                            "Redirects: unwraps redirect parameters, base64 targets, Google/Facebook/Outlook SafeLinks wrappers, intent:// fallbacks — and scans the destination",
                            "Schemes: javascript:, data:, intent://, USSD codes in tel:, ms-msdt and other abused handlers",
                            "Privacy: tracking parameters (strip with 'Copy clean link'), emails embedded in the link",
                        ).joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Sift judges the link, not the page. A clean result means no red flags in the address — not that the site is trustworthy.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    "Sift ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerSection(vm: SiftViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.store.serverUrl) }
    var token by rememberSaveable { mutableStateOf(vm.store.serverToken) }
    var auto by rememberSaveable { mutableStateOf(vm.store.autoSync) }
    val bl = vm.blocklist
    val dirty = url != vm.store.serverUrl || token != vm.store.serverToken || auto != vm.store.autoSync

    Section("Self-hosted blocklist server") {
        Text(
            "Point this at a text file you serve yourself (e.g. from a Pi over LAN or Tailscale). Sift downloads it; it never uploads anything.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Blocklist URL") }, placeholder = { Text("http://pi.lan:8088/blocklist.txt") },
            singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Bearer token (optional)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        if (url.startsWith("http://") && token.isNotEmpty()) {
            Text(
                "Heads up: the token goes over plain http. Fine on Tailscale or a trusted LAN; use https otherwise.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sync on launch", style = MaterialTheme.typography.bodyMedium)
                Text("When the list is older than 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = auto, onCheckedChange = { auto = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dirty) Button(onClick = { vm.saveServer(url, token, auto) }) { Text("Save") }
            FilledTonalButton(
                onClick = { vm.saveServer(url, token, auto); vm.sync() },
                enabled = !vm.busy && (url.startsWith("http://") || url.startsWith("https://")),
            ) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Sync now")
            }
            if (bl.syncedCount > 0) TextButton(onClick = vm::clearSynced) { Text("Remove") }
        }
        val status = when {
            bl.lastSyncError != null -> "Last attempt failed: ${bl.lastSyncError}"
            bl.lastSyncMillis > 0 -> "%,d entries · synced %s".format(
                bl.syncedCount, DateUtils.getRelativeTimeSpanString(bl.lastSyncMillis),
            )
            else -> "Never synced"
        }
        Text(
            status, style = MaterialTheme.typography.bodySmall,
            color = if (bl.lastSyncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
