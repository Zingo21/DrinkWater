package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.AuthState
import org.erbeenjoyers.drinkwater.normalizeServerUrl

/** Lets debug builds point the app at another server, e.g. a computer on the same wifi. */
data class ServerChoice(val current: String, val default: String, val onChange: (String) -> Unit)

/** @param server shown below the form when the server can be changed, i.e. in debug builds. */
@Composable
fun AuthScreen(
    state: AuthState,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String) -> Unit,
    onInputChanged: () -> Unit,
    server: ServerChoice? = null,
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val canSubmit = !state.busy && username.isNotBlank() && password.isNotEmpty()
    val submit = {
        if (canSubmit) {
            if (registering) onRegister(username, password) else onLogin(username, password)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 400.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DrinkWater", style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(8.dp))
            Text(
                if (registering) "Create an account" else "Log in to your account",
                style = MaterialTheme.typography.subtitle1,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    onInputChanged()
                },
                label = { Text("Username") },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onInputChanged()
                },
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (registering) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "At least 8 characters",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error, color = MaterialTheme.colors.error, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (registering) "Create account" else "Log in")
                }
            }
            TextButton(
                onClick = {
                    registering = !registering
                    onInputChanged()
                },
                enabled = !state.busy,
            ) {
                Text(if (registering) "Already have an account? Log in" else "New here? Create an account")
            }

            if (server != null) {
                var editing by rememberSaveable { mutableStateOf(false) }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { editing = true }, enabled = !state.busy) {
                    Text("Server: ${server.current}", style = MaterialTheme.typography.caption)
                }
                if (editing) {
                    ServerDialog(
                        server,
                        onDismiss = { editing = false },
                        onSave = { url ->
                            editing = false
                            if (url != server.current) server.onChange(url)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerDialog(server: ServerChoice, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(server.current) }
    val url = normalizeServerUrl(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server") },
        text = {
            Column {
                Text(
                    "Only debug builds can change this. To use a server on your computer from a phone, " +
                        "enter the computer's IP address, e.g. http://192.168.1.20:8080.",
                    style = MaterialTheme.typography.body2,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    isError = url == null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { url?.let(onSave) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (url == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Enter http:// or https:// followed by an address and port",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { url?.let(onSave) }, enabled = url != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (server.current != server.default) {
                    TextButton(onClick = { onSave(server.default) }) { Text("Use default") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
