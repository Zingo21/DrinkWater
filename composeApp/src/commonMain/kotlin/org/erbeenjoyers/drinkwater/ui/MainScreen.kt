package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Box
import org.erbeenjoyers.drinkwater.NotificationPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import org.erbeenjoyers.drinkwater.AccountDialog
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
fun MainScreen(
    user: UserDto,
    viewModel: AppViewModel,
    scaffoldState: ScaffoldState,
    onRemindersEnabled: () -> Unit,
    notificationPrompt: NotificationPrompt? = null,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Drink) }
    var editingReminders by remember { mutableStateOf(false) }
    var promptState by remember { mutableStateOf(NotificationPrompt.State.HIDDEN) }
    LaunchedEffect(notificationPrompt) {
        promptState = notificationPrompt?.state() ?: NotificationPrompt.State.HIDDEN
    }

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
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(onClick = {
                                menuOpen = false
                                viewModel.openAccountDialog(AccountDialog.ChangePassword)
                            }) { Text("Change password") }
                            DropdownMenuItem(onClick = {
                                menuOpen = false
                                viewModel.openAccountDialog(AccountDialog.LogOutOthers)
                            }) { Text("Log out other devices") }
                            DropdownMenuItem(onClick = {
                                menuOpen = false
                                viewModel.openAccountDialog(AccountDialog.DeleteAccount)
                            }) { Text("Delete account", color = MaterialTheme.colors.error) }
                            DropdownMenuItem(onClick = {
                                menuOpen = false
                                viewModel.logout()
                            }) { Text("Log out") }
                        }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            NotificationBanner(
                state = promptState,
                onTurnOn = {
                    promptState = NotificationPrompt.State.HIDDEN
                    notificationPrompt?.ask { granted -> if (granted) viewModel.enablePush() }
                },
                onDismiss = {
                    promptState = NotificationPrompt.State.HIDDEN
                    notificationPrompt?.dismiss()
                },
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
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
    }

    when (viewModel.account.dialog) {
        AccountDialog.ChangePassword -> ChangePasswordDialog(
            state = viewModel.account,
            onChange = viewModel::changePassword,
            onDismiss = viewModel::closeAccountDialog,
        )
        AccountDialog.LogOutOthers -> LogOutOthersDialog(
            state = viewModel.account,
            onLogOut = viewModel::logOutOtherDevices,
            onDismiss = viewModel::closeAccountDialog,
        )
        AccountDialog.DeleteAccount -> DeleteAccountDialog(
            state = viewModel.account,
            onDelete = viewModel::deleteAccount,
            onDismiss = viewModel::closeAccountDialog,
        )
        null -> Unit
    }

    if (editingReminders) {
        RemindersDialog(
            current = viewModel.reminderSettings,
            onSave = { settings ->
                viewModel.changeReminderSettings(settings)
                if (settings.enabled) {
                    onRemindersEnabled()
                    // Browsers: the reminders are pushes, so they need the permission the banner asks for. Asking
                    // again once it is granted just answers yes, and this is still the click browsers require.
                    notificationPrompt?.ask { granted ->
                        if (granted) viewModel.enablePush() else viewModel.notificationsDenied()
                    }
                    promptState = NotificationPrompt.State.HIDDEN
                }
                editingReminders = false
            },
            onDismiss = { editingReminders = false },
        )
    }
}
