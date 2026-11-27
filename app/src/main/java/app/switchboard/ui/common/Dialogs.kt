package app.switchboard.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.switchboard.ui.theme.Inter
import app.switchboard.ui.theme.Sbt

data class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

/** A bottom sheet with a list of actions, like a long-press menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit, header: (@Composable () -> Unit)? = null) {
    val c = Sbt.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            if (header != null) header()
            if (title != null) {
                Text(
                    title, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.text,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), maxLines = 2,
                )
            }
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); a.onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (a.icon != null) {
                        Icon(a.icon, null, tint = if (a.danger) c.danger else c.textMuted, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(18.dp))
                    }
                    Column {
                        Text(a.label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (a.danger) c.danger else c.text)
                        if (a.subtitle != null) Text(a.subtitle, style = Sbt.small, color = c.textMuted)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String? = null,
    hint: String? = null,
    confirm: String = "Save",
    allowEmpty: Boolean = false,
    message: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) { Text(message, style = Sbt.small, color = Sbt.c.textMuted); Spacer(Modifier.size(12.dp)) }
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = label?.let { { Text(it) } }, placeholder = hint?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(enabled = allowEmpty || text.isNotBlank(), onClick = { onDismiss(); onConfirm(text) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String?, confirm: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (danger) Sbt.c.danger else Sbt.c.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Progress for bulk actions ("Leaving 12 of 40"). */
@Composable
fun ProgressDialog(title: String, done: Int, total: Int, note: String? = null) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(10.dp))
                Text("$done of $total", style = Sbt.small)
                if (note != null) Text(note, style = Sbt.small, color = Sbt.c.textMuted)
            }
        },
        confirmButton = {},
    )
}
