@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.sift.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.sift.BuildConfig
import app.sift.SiftViewModel
import app.sift.engine.CleanUrl
import app.sift.engine.Finding
import app.sift.engine.Report
import app.sift.engine.Severity
import app.sift.engine.Verdict
import java.net.IDN
import kotlinx.coroutines.launch

@Composable
fun SiftRoot(vm: SiftViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        vm.message = null
        // Launched outside the effect so clearing the message doesn't cancel the snackbar.
        scope.launch { snackbar.showSnackbar(m) }
    }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(vm, snackbar, onBack = { showSettings = false })
    } else {
        HomeScreen(vm, snackbar, onSettings = { showSettings = true })
    }
}

@Composable
private fun HomeScreen(vm: SiftViewModel, snackbar: SnackbarHostState, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var confirmOpen by remember { mutableStateOf<Report?>(null) }

    val actions = ReportActions(
        copy = { text, label -> clipboard.setText(AnnotatedString(text)); vm.message = "$label copied" },
        share = { r -> shareReport(ctx, r) },
        open = { r -> if (r.verdict >= Verdict.SUSPICIOUS || r.findings.any { it.severity >= Severity.HIGH }) confirmOpen = r else openInBrowser(ctx, r.analyzed) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sift", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InputCard(
                    value = vm.input,
                    onChange = { vm.input = it },
                    onScan = { focus.clearFocus(); vm.scan(vm.input) },
                    onPaste = {
                        val t = clipboard.getText()?.text
                        if (t.isNullOrBlank()) vm.message = "Clipboard is empty" else { focus.clearFocus(); vm.scan(t) }
                    },
                    onClear = { vm.clear() },
                )
            }
            if (!vm.ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            val reports = vm.reports
            if (reports.isEmpty()) {
                item { IntroCard(vm.blocklist.total) }
            } else {
                if (reports.size > 1) {
                    item { ReportPicker(reports, vm.selected) { vm.selected = it } }
                }
                val r = reports[vm.selected.coerceIn(0, reports.size - 1)]
                reportItems(r, actions)
            }
        }
    }

    confirmOpen?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmOpen = null },
            icon = { Icon(Icons.Filled.Warning, null, tint = r.verdict.color()) },
            title = { Text("Open a ${r.verdict.label.lowercase()} link?") },
            text = { Text("Sift found serious problems with this link. Opening it hands it to your browser, which will contact the site.\n\n${r.displayHost ?: r.analyzed}") },
            confirmButton = { TextButton(onClick = { confirmOpen = null; openInBrowser(ctx, r.analyzed) }) { Text("Open anyway") } },
            dismissButton = { Button(onClick = { confirmOpen = null }) { Text("Don't open") } },
        )
    }
}

private class ReportActions(
    val copy: (String, String) -> Unit,
    val share: (Report) -> Unit,
    val open: (Report) -> Unit,
)

@Composable
private fun InputCard(value: String, onChange: (String) -> Unit, onScan: () -> Unit, onPaste: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a link or some text containing links") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            minLines = 2,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { onScan() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onPaste) { Text("Paste & scan") }
            Spacer(Modifier.weight(1f))
            if (value.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            Button(onClick = onScan, enabled = value.isNotBlank()) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan")
            }
        }
    }
}

@Composable
private fun IntroCard(blocklistSize: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Scans happen on this phone", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Sift never opens, resolves, or uploads the links you give it. It reads the link itself and looks for " +
                    "phishing tricks, lookalike domains, disguised addresses, malicious downloads, and attack payloads.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (BuildConfig.SELF_HOSTED) "This build only uses the network to pull a blocklist from a server you set in Settings."
                else "This build has no internet permission at all — Android won't let it connect to anything.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("How to use", style = MaterialTheme.typography.titleSmall)
            Text(
                "• Share a link from any app → Sift\n• Select a link in any text → ⋮ → Scan link with Sift\n• Or paste it above",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (blocklistSize > 0) "Blocklist loaded: %,d entries".format(blocklistSize)
                else "No blocklist loaded — heuristics only. Add one in Settings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportPicker(reports: List<Report>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("${reports.size} links found", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reports.forEachIndexed { i, r ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    label = { Text(r.displayHost ?: r.scheme ?: "link", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Dot(r.verdict.color()) },
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

private fun LazyListScope.reportItems(r: Report, actions: ReportActions) {
    item(key = "verdict") { VerdictCard(r) }
    item(key = "dest") { DestinationCard(r) }
    item(key = "actions") { ActionsRow(r, actions) }
    if (r.nested.isNotEmpty()) {
        item(key = "nested-h") { SectionHeader("Leads to") }
        items(r.nested.size, key = { "nested-$it" }) { i -> NestedCard(r.nested[i], depth = 0) }
    }
    item(key = "findings-h") { SectionHeader(if (r.findings.isEmpty()) "Findings" else "Findings (${r.findings.size})") }
    if (r.findings.isEmpty()) {
        item(key = "none") {
            Text(
                "Nothing flagged. Sift can't see the page itself, so stay alert for requests for passwords, codes, or payment.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(r.findings, key = { "f-" + it.title }) { FindingCard(it) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun VerdictCard(r: Report) {
    val col = r.verdict.color()
    Card(colors = CardDefaults.cardColors(containerColor = col.copy(alpha = 0.14f))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (r.verdict == Verdict.LOOKS_CLEAN) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null, tint = col, modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(r.verdict.label, style = MaterialTheme.typography.headlineSmall, color = col, fontWeight = FontWeight.Bold)
                    val serious = r.findings.count { it.severity >= Severity.MEDIUM }
                    Text(
                        "Risk ${r.score}/100 · $serious serious finding${if (serious == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(r.verdict.blurb, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { r.score / 100f },
                color = col,
                trackColor = col.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun DestinationCard(r: Report) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Where this really goes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.host != null) {
                Text(hostAnnotated(r), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                r.registrableDomain?.let { reg ->
                    Text(
                        "Owner: ${unicodeOf(reg)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Only the highlighted part decides who controls the site. Everything to its left is picked by that owner — it can say anything.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (r.unicodeHost != null) {
                    Text(
                        "Displays as ${r.unicodeHost} — real name ${r.host}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text("Not a web address (${r.scheme}:)", style = MaterialTheme.typography.titleMedium)
            }
            SelectionContainer {
                Text(
                    r.analyzed, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    maxLines = 8, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun hostAnnotated(r: Report): AnnotatedString {
    val display = r.displayHost ?: ""
    val reg = r.registrableDomain?.let { unicodeOf(it) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        if (reg != null && display.endsWith(reg)) {
            withStyle(SpanStyle(color = dim)) { append(display.removeSuffix(reg)) }
            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(reg) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(display) }
        }
        r.port?.let { withStyle(SpanStyle(color = dim)) { append(":$it") } }
    }
}

private fun unicodeOf(ascii: String): String =
    runCatching { IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED) }.getOrDefault(ascii)

@Composable
private fun ActionsRow(r: Report, a: ReportActions) {
    val clean = remember(r.analyzed) { CleanUrl.clean(r.analyzed) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { a.copy(r.analyzed, "Link") }, label = { Text("Copy link") })
        AssistChip(onClick = { a.copy(r.defanged, "Defanged link") }, label = { Text("Copy defanged") })
        if (clean != r.analyzed) AssistChip(onClick = { a.copy(clean, "Clean link") }, label = { Text("Copy clean link") })
        AssistChip(onClick = { a.share(r) }, label = { Text("Share report") })
        if (r.scheme == "http" || r.scheme == "https") {
            AssistChip(onClick = { a.open(r) }, label = { Text("Open in browser") })
        }
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val col = f.severity.color()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(col))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeverityPill(f.severity)
                    Spacer(Modifier.width(8.dp))
                    Text(f.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(f.detail, style = MaterialTheme.typography.bodySmall)
                f.evidence?.let {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        SelectionContainer {
                            Text(
                                it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityPill(s: Severity) {
    val col = s.color()
    Surface(color = col.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(
            s.label.uppercase(), color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NestedCard(r: Report, depth: Int) {
    var expanded by rememberSaveable(r.input) { mutableStateOf(r.verdict >= Verdict.SUSPICIOUS) }
    val col = r.verdict.color()
    Card(colors = CardDefaults.cardColors(containerColor = col.copy(alpha = 0.10f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Dot(col, 12)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.displayHost ?: "${r.scheme}:", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.verdict.label} · risk ${r.score}/100", style = MaterialTheme.typography.bodySmall, color = col)
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (expanded) {
                Text(r.analyzed, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                r.findings.forEach { f ->
                    Row(verticalAlignment = Alignment.Top) {
                        Dot(f.severity.color(), 8)
                        Spacer(Modifier.width(8.dp))
                        Text(f.title, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (depth < 2) r.nested.forEach { NestedCard(it, depth + 1) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------- intents

private fun openInBrowser(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun shareReport(ctx: Context, r: Report) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Sift: ${r.verdict.label} link")
        putExtra(Intent.EXTRA_TEXT, r.toShareText())
    }
    ctx.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Report.toShareText(): String = buildString {
    appendLine("Sift link scan — ${verdict.label} (risk $score/100)")
    appendLine("Link (defanged): $defanged")
    host?.let { h -> appendLine("Host: $h" + (registrableDomain?.let { " · owner domain: $it" } ?: "")) }
    unicodeHost?.let { appendLine("Displays as: $it") }
    val shown = findings.filter { it.severity >= Severity.LOW }
    if (shown.isNotEmpty()) {
        appendLine()
        shown.forEach { appendLine("• [${it.severity.label}] ${it.title}") }
    }
    if (nested.isNotEmpty()) {
        appendLine()
        nested.forEach { appendLine("→ ${it.defanged}: ${it.verdict.label} (${it.score}/100)") }
    }
    appendLine()
    append("Analyzed on-device; the link was not opened.")
}
