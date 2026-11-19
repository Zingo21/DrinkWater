package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.NotificationPrompt

/** Invites the user to turn on notifications, or on an iPhone tells them how to make that possible. */
@Composable
fun NotificationBanner(state: NotificationPrompt.State, onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    if (state == NotificationPrompt.State.HIDDEN) return
    Surface(color = MaterialTheme.colors.primary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(
                when (state) {
                    NotificationPrompt.State.ASK ->
                        "Get a notification when a friend drinks or nudges you, even when DrinkWater is closed."
                    else ->
                        "To get notifications on iPhone, add DrinkWater to your Home Screen: tap Share, then " +
                            "Add to Home Screen, and open it from there."
                },
                style = MaterialTheme.typography.body2,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                if (state == NotificationPrompt.State.ASK) {
                    TextButton(onClick = onTurnOn) { Text("Turn on") }
                }
            }
        }
    }
}
