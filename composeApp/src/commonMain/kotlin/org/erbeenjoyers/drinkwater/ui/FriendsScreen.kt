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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.FriendsState
import org.erbeenjoyers.drinkwater.api.FriendRequestDto
import org.erbeenjoyers.drinkwater.api.LeaderboardEntryDto
import org.erbeenjoyers.drinkwater.api.UserDto

/** @param userId the logged-in user, who is on the leaderboard among their friends. */
@Composable
fun FriendsScreen(
    state: FriendsState,
    userId: Int,
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

        item(key = "leaderboard-header") { SectionHeader("Today's leaderboard") }
        itemsIndexed(state.leaderboard, key = { _, entry -> "friend-${entry.user.id}" }) { index, entry ->
            val friend = entry.user
            LeaderboardRow(rank = index + 1, entry = entry, isYou = friend.id == userId) {
                if (friend.id != userId) {
                    TextButton(onClick = { friendToRemove = friend }, enabled = !state.working) { Text("Remove") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onNudge(friend) }, enabled = !state.working) { Text("Nudge") }
                }
            }
        }
        if (state.leaderboard.none { it.user.id != userId }) {
            item(key = "no-friends") {
                Text(
                    "No friends yet. Add someone by their username to see when they drink and nudge each other.",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(top = 8.dp),
                )
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

/** A person's place on the leaderboard, with how far they are towards their own goal today. */
@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntryDto, isYou: Boolean, actions: @Composable RowScope.() -> Unit) {
    val muted = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MaterialTheme.typography.h6, color = muted, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isYou) "${entry.user.username} (you)" else entry.user.username,
                style = MaterialTheme.typography.body1,
                fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("${entry.todayMl} of ${entry.goalMl} ml")
                    if (entry.streakDays > 0) append(" · ${entry.streakDays}-day streak")
                },
                style = MaterialTheme.typography.caption,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = (entry.todayMl.toFloat() / entry.goalMl).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        actions()
    }
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
