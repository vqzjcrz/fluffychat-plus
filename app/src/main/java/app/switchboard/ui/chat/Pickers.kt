package app.switchboard.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone

/** Picks a date, then a time; returns the moment in local time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val now = Calendar.getInstance()
    val timeState = rememberTimePickerState(initialHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24, initialMinute = 0)
    if (dateMillis == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { dateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis() }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time") },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    // The date picker gives midnight UTC of the chosen day.
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis!! }
                    val local = Calendar.getInstance().apply {
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onDismiss()
                    onPicked(local.timeInMillis)
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
