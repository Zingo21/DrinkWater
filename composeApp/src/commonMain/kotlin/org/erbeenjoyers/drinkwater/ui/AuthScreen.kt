package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
fun AuthScreen(
    state: AuthState,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String) -> Unit,
    onInputChanged: () -> Unit,
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
        }
    }
}
