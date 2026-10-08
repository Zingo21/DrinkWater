package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.FriendsState
import org.erbeenjoyers.drinkwater.api.FriendRequestDto
import org.erbeenjoyers.drinkwater.api.UserDto

@Composable
fun FriendsScreen(
    state: FriendsState,
    onAddFriend: (username: String, onSent: () -> Unit) -> Unit,
    onAccept: (FriendRequestDto) -> Unit,
    onDeleteRequest: (FriendRequestDto) -> Unit,
    onRemoveFriend: (UserDto) -> Unit,
    onNudge: (UserDto) -> Unit,
    onRetry: () -> Unit,
) {
    if (!state.loaded) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loadError == null) {
                CircularProgressIndicator()
            } else {
                Text(state.loadError, color = MaterialTheme.colors.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
        return
    }

    var friendToRemove by remember { mutableStateOf<UserDto?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        item(key = "add") { AddFriendRow(enabled = !state.working, onAddFriend) }

        if (state.incoming.isNotEmpty()) {
            item(key = "incoming-header") { SectionHeader("Requests to you") }
            items(state.incoming, key = { "incoming-${it.id}" }) { request ->
                PersonRow(request.from.username) {
                    TextButton(onClick = { onDeleteRequest(request) }, enabled = !state.working) { Text("Decline") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAccept(request) }, enabled = !state.working) { Text("Accept") }
                }
            }
        }

        if (state.outgoing.isNotEmpty()) {
            item(key = "outgoing-header") { SectionHeader("Sent requests") }
            items(state.outgoing, key = { "outgoing-${it.id}" }) { request ->
                PersonRow(request.to.username) {
                    TextButton(onClick = { onDeleteRequest(request) }, enabled = !state.working) { Text("Cancel") }
                }
            }
        }

        item(key = "friends-header") { SectionHeader("Friends") }
        if (state.friends.isEmpty()) {
            item(key = "no-friends") {
                Text(
                    "No friends yet. Add someone by their username to see when they drink and nudge each other.",
                    style = MaterialTheme.typography.body2,
                )
            }
        }
        items(state.friends, key = { "friend-${it.id}" }) { friend ->
            PersonRow(friend.username) {
                TextButton(onClick = { friendToRemove = friend }, enabled = !state.working) { Text("Remove") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onNudge(friend) }, enabled = !state.working) { Text("Nudge") }
            }
        }
    }

    friendToRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Remove ${friend.username}?") },
            text = { Text("You'll stop seeing each other's drinks. You can send a new friend request later.") },
            confirmButton = {
                TextButton(onClick = {
                    friendToRemove = null
                    onRemoveFriend(friend)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun AddFriendRow(enabled: Boolean, onAddFriend: (username: String, onSent: () -> Unit) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    val canSubmit = enabled && username.isNotBlank()
    val submit = {
        if (canSubmit) onAddFriend(username) { username = "" }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Add a friend by username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = submit, enabled = canSubmit) { Text("Add") }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun PersonRow(username: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            username,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
