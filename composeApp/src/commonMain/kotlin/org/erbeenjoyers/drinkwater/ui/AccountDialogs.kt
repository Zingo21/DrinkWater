package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.AccountState

private const val MIN_PASSWORD_LENGTH = 8

@Composable
fun ChangePasswordDialog(
    state: AccountState,
    onChange: (currentPassword: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }

    val mismatch = repeated.isNotEmpty() && repeated != new
    val canSave = !state.busy && current.isNotEmpty() && new.length >= MIN_PASSWORD_LENGTH && new == repeated

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                Text(
                    "Your other phones and tablets will be logged out.",
                    style = MaterialTheme.typography.body2,
                )
                Spacer(Modifier.height(12.dp))
                PasswordField("Current password", current, { current = it }, enabled = !state.busy)
                Spacer(Modifier.height(8.dp))
                PasswordField("New password", new, { new = it }, enabled = !state.busy)
                Text("At least $MIN_PASSWORD_LENGTH characters", style = MaterialTheme.typography.caption)
                Spacer(Modifier.height(8.dp))
                PasswordField("Repeat new password", repeated, { repeated = it }, enabled = !state.busy, isError = mismatch)
                if (mismatch) {
                    Text("The passwords don't match", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(current, new) }, enabled = canSave) {
                Text(if (state.busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") }
        },
    )
}

@Composable
fun DeleteAccountDialog(state: AccountState, onDelete: (password: String) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
        text = {
            Column {
                Text(
                    "This deletes your account, your drink history and your friendships. It can't be undone.",
                    style = MaterialTheme.typography.body2,
                )
                Spacer(Modifier.height(12.dp))
                PasswordField("Password", password, { password = it }, enabled = !state.busy)
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(password) }, enabled = !state.busy && password.isNotEmpty()) {
                Text(if (state.busy) "Deleting…" else "Delete account", color = MaterialTheme.colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
