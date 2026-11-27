package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.ThemeChoice

/** Light, dark or the device's own setting. A pick applies at once, so the dialog only needs a way out. */
@Composable
fun AppearanceDialog(current: ThemeChoice, onChoose: (ThemeChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeChoice.entries.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == current, onClick = { onChoose(choice) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == current,
                            onClick = null,
                            // The app's purple rather than Material's teal secondary colour.
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                        )
                        Text(choice.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
