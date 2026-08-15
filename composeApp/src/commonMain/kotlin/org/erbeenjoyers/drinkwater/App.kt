package org.erbeenjoyers.drinkwater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.erbeenjoyers.drinkwater.dto.NotificationType

@Composable
fun App() {
    MaterialTheme {
        val viewModel: DrinkWaterViewModel = viewModel { DrinkWaterViewModel() }
        val state by viewModel.uiState.collectAsState()

        // Försök ladda sparad användare vid start.
        LaunchedEffect(Unit) {
            val storage = createUserIdStorage()
            storage.load()?.let { viewModel.loadPersistedUser(it) }
        }

        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.currentUserId == null -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state.currentUserId == null -> {
                        LoginScreen(onCreate = viewModel::createUser)
                    }
                    else -> {
                        MainScreen(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                // Visa tillfälliga meddelanden som en snackbar-liknande text.
                state.message?.let { message ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    ) { Text(message) }
                    LaunchedEffect(message) {
                        kotlinx.coroutines.delay(2500)
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onCreate: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Välkommen till Drink Water!", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Text("Skapa en användare för att börja dricka med vänner.",
            style = MaterialTheme.typography.body2)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Användarnamn") },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (username.isNotBlank()) onCreate(username.trim()) },
            enabled = username.isNotBlank(),
        ) { Text("Skapa / logga in") }
    }
}

@Composable
private fun MainScreen(
    state: DrinkWaterUiState,
    viewModel: DrinkWaterViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // --- Status: poäng och tur ---
        state.currentUser?.let { user ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Inloggad: ${user.username}", fontWeight = FontWeight.Bold)
                    Text("Poäng: ${user.score}")
                    Text(
                        if (user.isYourTurn) "🔔 Det är DIN tur att dricka!"
                        else "Bra jobbat, du har druckit senast."
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- Dryckesväljare + drick-knapp ---
        Text("Drick vatten", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        if (state.drinks.isEmpty()) {
            Text("Hämtar drycker…")
        } else {
            state.drinks.forEach { drink ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedDrinkId == drink.id,
                        onClick = { viewModel.selectDrink(drink.id) },
                    )
                    Text(drink.name)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::drink,
            enabled = state.selectedDrinkId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Jag har druckit! 💧")
        }
        Spacer(Modifier.height(24.dp))

        // --- Vänner ---
        Text("Vänner", style = MaterialTheme.typography.h6)
        FriendSection(state, viewModel)
        Spacer(Modifier.height(24.dp))

        // --- Notifieringar ---
        Text("Notifieringar", style = MaterialTheme.typography.h6)
        NotificationSection(state, viewModel)
    }
}

@Composable
private fun FriendSection(state: DrinkWaterUiState, viewModel: DrinkWaterViewModel) {
    var newFriend by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newFriend,
                onValueChange = { newFriend = it },
                label = { Text("Vännens användarnamn") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newFriend.isNotBlank()) {
                        viewModel.addFriend(newFriend.trim())
                        newFriend = ""
                    }
                },
                enabled = newFriend.isNotBlank(),
            ) { Text("Lägg till") }
        }
        Spacer(Modifier.height(12.dp))
        if (state.friends.isEmpty()) {
            Text("Du har inga vänner ännu. Lägg till någon ovan!")
        } else {
            state.friends.forEach { friend ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(friend.friendUsername, fontWeight = FontWeight.Medium)
                            Text("Poäng: ${friend.friendScore}", style = MaterialTheme.typography.caption)
                            if (friend.isFriendTurn) {
                                Text(
                                    "🔔 Det är ${friend.friendUsername}s tur att dricka",
                                    color = MaterialTheme.colors.primary,
                                    style = MaterialTheme.typography.caption,
                                )
                            }
                        }
                        OutlinedButton(onClick = { viewModel.remindFriend(friend) }) {
                            Text("Påminn")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.removeFriend(friend.friendId) }) {
                            Text("Ta bort", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(state: DrinkWaterUiState, viewModel: DrinkWaterViewModel) {
    if (state.notifications.isEmpty()) {
        Text("Inga notifieringar.")
    } else {
        state.notifications.take(20).forEach { notification ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val icon = when (notification.type) {
                            NotificationType.DRANK -> "💧"
                            NotificationType.YOUR_TURN -> "🔔"
                        }
                        Text("$icon ${notification.fromUsername}", fontWeight = FontWeight.Medium)
                        Text(notification.message, style = MaterialTheme.typography.caption)
                    }
                    if (!notification.read) {
                        OutlinedButton(onClick = { viewModel.markNotificationRead(notification.id) }) {
                            Text("Läst")
                        }
                    }
                }
            }
        }
    }
}
