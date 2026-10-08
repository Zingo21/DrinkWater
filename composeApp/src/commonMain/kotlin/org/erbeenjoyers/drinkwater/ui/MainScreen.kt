package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleStartEffect
import org.erbeenjoyers.drinkwater.AppViewModel
import org.erbeenjoyers.drinkwater.api.UserDto

private enum class Tab(val label: String, val icon: ImageVector) {
    Drink("Drink", Icons.Filled.Home),
    Stats("Stats", Icons.Filled.DateRange),
    Friends("Friends", Icons.Filled.Person),
    Feed("Feed", Icons.Filled.Notifications),
}

/**
 * Everything a logged-in [user] sees: the tabs and the bars around them.
 *
 * @param onRemindersEnabled called when the user turns their drink reminders on.
 */
@Composable
fun MainScreen(user: UserDto, viewModel: AppViewModel, scaffoldState: ScaffoldState, onRemindersEnabled: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Drink) }
    var editingReminders by remember { mutableStateOf(false) }

    // Live notifications only while the app is on screen.
    LifecycleStartEffect(viewModel) {
        viewModel.startListening()
        onStopOrDispose { viewModel.stopListening() }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text("Hi, ${user.username}") },
                actions = {
                    if (viewModel.canRemind) {
                        IconButton(onClick = { editingReminders = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Reminders")
                        }
                    }
                    TextButton(onClick = viewModel::logout) {
                        Text("Log out", color = MaterialTheme.colors.onPrimary)
                    }
                },
            )
        },
        bottomBar = {
            BottomNavigation {
                Tab.entries.forEach { item ->
                    val requests = if (item == Tab.Friends) viewModel.friends.incoming.size else 0
                    BottomNavigationItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = { Text(item.label) },
                        icon = {
                            BadgedBox(badge = { if (requests > 0) Badge { Text(requests.toString()) } }) {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Drink -> DrinkScreen(
                    state = viewModel.home,
                    stats = viewModel.stats.stats,
                    onLogDrink = viewModel::logDrink,
                    onRetry = viewModel::loadDrinks,
                )

                Tab.Stats -> StatsScreen(
                    state = viewModel.stats,
                    onSetGoal = viewModel::setDailyGoal,
                    onDeleteDrink = viewModel::deleteDrink,
                    onRetry = viewModel::refreshStats,
                )

                Tab.Friends -> FriendsScreen(
                    state = viewModel.friends,
                    userId = user.id,
                    onAddFriend = viewModel::addFriend,
                    onAccept = viewModel::acceptRequest,
                    onDeleteRequest = viewModel::deleteRequest,
                    onRemoveFriend = viewModel::removeFriend,
                    onNudge = viewModel::nudge,
                    onRetry = viewModel::refreshFriends,
                )

                Tab.Feed -> FeedScreen(viewModel.feed)
            }
        }
    }

    if (editingReminders) {
        RemindersDialog(
            current = viewModel.reminderSettings,
            onSave = { settings ->
                viewModel.changeReminderSettings(settings)
                if (settings.enabled) onRemindersEnabled()
                editingReminders = false
            },
            onDismiss = { editingReminders = false },
        )
    }
}
