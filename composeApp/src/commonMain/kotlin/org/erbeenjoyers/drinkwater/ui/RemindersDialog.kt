package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.REMINDER_INTERVALS_MINUTES
import org.erbeenjoyers.drinkwater.ReminderSettings

/** Lets the user turn their own drink reminders on and choose when they come. */
@Composable
fun RemindersDialog(current: ReminderSettings, onSave: (ReminderSettings) -> Unit, onDismiss: () -> Unit) {
    var settings by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me to drink", modifier = Modifier.weight(1f))
                    Switch(checked = settings.enabled, onCheckedChange = { settings = settings.copy(enabled = it) })
                }
                if (settings.enabled) {
                    Spacer(Modifier.height(8.dp))
                    // An interval saved by another version of the app may not be in the list; step from the nearest.
                    val index = REMINDER_INTERVALS_MINUTES.indexOfLast { it <= settings.intervalMinutes }.coerceAtLeast(0)
                    Stepper(
                        label = "When I haven't for",
                        value = intervalLabel(settings.intervalMinutes),
                        onLess = REMINDER_INTERVALS_MINUTES.getOrNull(index - 1)?.let { { settings = settings.copy(intervalMinutes = it) } },
                        onMore = REMINDER_INTERVALS_MINUTES.getOrNull(index + 1)?.let { { settings = settings.copy(intervalMinutes = it) } },
                    )
                    Stepper(
                        label = "Not before",
                        value = hourLabel(settings.startHour),
                        onLess = { settings = settings.copy(startHour = settings.startHour - 1) }.takeIf { settings.startHour > 0 },
                        onMore = { settings = settings.copy(startHour = settings.startHour + 1) }
                            .takeIf { settings.startHour < settings.endHour - 1 },
                    )
                    Stepper(
                        label = "Not after",
                        value = hourLabel(settings.endHour),
                        onLess = { settings = settings.copy(endHour = settings.endHour - 1) }
                            .takeIf { settings.endHour > settings.startHour + 1 },
                        onMore = { settings = settings.copy(endHour = settings.endHour + 1) }.takeIf { settings.endHour < 23 },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reminders stop for the day once you reach your goal.",
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(settings) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** A value with a button on each side to step it down or up. A null action disables its button. */
@Composable
private fun Stepper(label: String, value: String, onLess: (() -> Unit)?, onMore: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onLess?.invoke() },
            enabled = onLess != null,
            modifier = Modifier.semantics { contentDescription = "$label: less" },
        ) { Text("−") }
        Text(value, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp))
        TextButton(
            onClick = { onMore?.invoke() },
            enabled = onMore != null,
            modifier = Modifier.semantics { contentDescription = "$label: more" },
        ) { Text("+") }
    }
}

/** `30 min`, `2 h`, `1 h 30` */
private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

private fun hourLabel(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"
