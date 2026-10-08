package org.erbeenjoyers.drinkwater

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogEntryDto
import org.erbeenjoyers.drinkwater.api.FriendRequestDto
import org.erbeenjoyers.drinkwater.api.FriendRequestResult
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.StatsDto
import org.erbeenjoyers.drinkwater.api.UserDto
import org.erbeenjoyers.drinkwater.api.describe
import org.erbeenjoyers.drinkwater.client.ApiError
import org.erbeenjoyers.drinkwater.client.DrinkWaterApi

data class AuthState(val busy: Boolean = false, val error: String? = null)

data class HomeState(
    val drinks: List<DrinkDto> = emptyList(),
    val loading: Boolean = false,
    val loadError: String? = null,
    /** True while a drink is being sent to the server. */
    val logging: Boolean = false,
)

data class StatsState(
    /** Null until the stats have been fetched once. */
    val stats: StatsDto? = null,
    /** The user's latest drinks, newest first. */
    val recent: List<DrinkLogEntryDto> = emptyList(),
    val loadError: String? = null,
    /** True while a change (new goal, deleted drink) is being sent to the server. */
    val working: Boolean = false,
)

data class FriendsState(
    val friends: List<UserDto> = emptyList(),
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
    /** False until the lists have been fetched once; later refreshes keep showing the old lists. */
    val loaded: Boolean = false,
    val loadError: String? = null,
    /** True while a change (add, accept, decline, remove) is being sent to the server. */
    val working: Boolean = false,
)

data class FeedItem(val id: Long, val notification: Notification, val receivedAt: Instant)

data class FeedState(
    /** Newest first. Only holds what arrived while the app was open. */
    val items: List<FeedItem> = emptyList(),
    val connected: Boolean = false,
)

class AppViewModel(
    private val api: DrinkWaterApi,
    private val sessions: SessionStore,
    /** Null on platforms where push notifications aren't set up. */
    private val push: PushRegistration? = null,
) : ViewModel() {
    /** The logged-in user, or null while logged out. */
    var user by mutableStateOf<UserDto?>(null)
        private set
    var auth by mutableStateOf(AuthState())
        private set
    var home by mutableStateOf(HomeState())
        private set
    var stats by mutableStateOf(StatsState())
        private set
    var friends by mutableStateOf(FriendsState())
        private set
    var feed by mutableStateOf(FeedState())
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages to show in a snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Parent of every coroutine that works with the logged-in user's data, so logging out stops them all. */
    private var sessionJob = newSessionJob()
    private var listening: Job? = null
    private var nextFeedId = 0L

    /** The push token the server currently has for this device, if any. */
    private var registeredPushToken: String? = null

    init {
        sessions.load()?.let { session ->
            api.token = session.token
            user = session.user
            loadDrinks()
            refreshStats()
            refreshFriends()
            registerForPush()
        }
    }

    fun login(username: String, password: String) = authenticate { api.login(username.trim(), password) }

    fun register(username: String, password: String) = authenticate { api.register(username.trim(), password) }

    fun clearAuthError() {
        if (auth.error != null) auth = auth.copy(error = null)
    }

    fun logout() {
        stopPushToThisDevice()
        sessionJob.cancel()
        sessionJob = newSessionJob()
        listening = null
        sessions.clear()
        api.token = null
        user = null
        home = HomeState()
        stats = StatsState()
        friends = FriendsState()
        feed = FeedState()
    }

    fun loadDrinks() {
        if (home.loading) return
        home = home.copy(loading = true, loadError = null)
        launchInSession {
            home = try {
                val drinks = api.drinks()
                home.copy(drinks = drinks, loading = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                home.copy(loading = false, loadError = e.userMessage())
            }
        }
    }

    fun logDrink(drink: DrinkDto, amountMl: Int) {
        if (home.logging) return
        home = home.copy(logging = true)
        launchInSession {
            try {
                val log = api.logDrink(drink.id, amountMl, timeZone())
                _messages.send(
                    if (log.goalReached) "Daily goal reached!" else "Logged: $amountMl ml ${drink.name}",
                )
                refreshStats()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!expireSessionIfUnauthorized(e)) _messages.send(e.userMessage())
            } finally {
                home = home.copy(logging = false)
            }
        }
    }

    fun refreshStats() {
        stats = stats.copy(loadError = null)
        launchInSession {
            try {
                val (current, recent) = coroutineScope {
                    val current = async { api.stats(timeZone()) }
                    val recent = async { api.drinkHistory(limit = RECENT_DRINKS) }
                    current.await() to recent.await()
                }
                stats = stats.copy(stats = current, recent = recent)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!expireSessionIfUnauthorized(e)) stats = stats.copy(loadError = e.userMessage())
            }
        }
    }

    /** [onSaved] runs once the server has stored the goal, e.g. to close the dialog. */
    fun setDailyGoal(goalMl: Int, onSaved: () -> Unit) = changeStats {
        api.setDailyGoal(goalMl)
        onSaved()
        "Daily goal set to $goalMl ml"
    }

    fun deleteDrink(entry: DrinkLogEntryDto) = changeStats {
        api.deleteDrinkLog(entry.id)
        "Removed ${entry.amountMl} ml ${entry.drink.name}"
    }

    fun refreshFriends() {
        friends = friends.copy(loadError = null)
        launchInSession {
            try {
                val (list, requests) = coroutineScope {
                    val list = async { api.friends() }
                    val requests = async { api.friendRequests() }
                    list.await() to requests.await()
                }
                friends = friends.copy(
                    friends = list,
                    incoming = requests.incoming,
                    outgoing = requests.outgoing,
                    loaded = true,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!expireSessionIfUnauthorized(e)) friends = friends.copy(loadError = e.userMessage())
            }
        }
    }

    /** [onSent] runs once the server has accepted the request, e.g. to clear the input field. */
    fun addFriend(username: String, onSent: () -> Unit) = changeFriends {
        val response = api.sendFriendRequest(username.trim())
        onSent()
        when (response.result) {
            FriendRequestResult.SENT -> "Friend request sent to ${response.request.to.username}"
            FriendRequestResult.ACCEPTED -> "You and ${response.request.to.username} are now friends"
        }
    }

    fun acceptRequest(request: FriendRequestDto) = changeFriends {
        val friend = api.acceptFriendRequest(request.id)
        "You and ${friend.username} are now friends"
    }

    /** Declines an incoming request or cancels an outgoing one. */
    fun deleteRequest(request: FriendRequestDto) = changeFriends {
        api.deleteFriendRequest(request.id)
        null
    }

    fun removeFriend(friend: UserDto) = changeFriends {
        api.removeFriend(friend.id)
        "Removed ${friend.username}"
    }

    /** Reminds [friend] to drink. */
    fun nudge(friend: UserDto) = changeFriends {
        api.nudge(friend.id)
        "Nudged ${friend.username}"
    }

    /**
     * Keeps a connection for live notifications open until [stopListening] or logout, reconnecting
     * when it drops. Meant to follow the app being on screen.
     */
    fun startListening() {
        if (listening?.isActive == true) return
        // The app is back on screen, possibly on a new day.
        refreshStats()
        listening = launchInSession {
            var retryDelayMs = INITIAL_RETRY_DELAY_MS
            while (true) {
                try {
                    api.notifications(onConnected = {
                        retryDelayMs = INITIAL_RETRY_DELAY_MS
                        feed = feed.copy(connected = true)
                        // Catches up on anything that changed while disconnected, and notices an expired token.
                        refreshFriends()
                    }).collect(::onNotification)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                } finally {
                    feed = feed.copy(connected = false)
                }
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    fun stopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onCleared() {
        api.close()
    }

    private suspend fun onNotification(notification: Notification) {
        val item = FeedItem(nextFeedId++, notification, Clock.System.now())
        feed = feed.copy(items = (listOf(item) + feed.items).take(MAX_FEED_ITEMS))
        if (notification !is Notification.FriendDrank) refreshFriends()
        _messages.send(notification.describe())
    }

    private fun authenticate(request: suspend () -> AuthResponse) {
        if (auth.busy) return
        auth = AuthState(busy = true)
        viewModelScope.launch {
            auth = try {
                val session = request()
                sessions.save(session)
                user = session.user
                loadDrinks()
                refreshStats()
                refreshFriends()
                registerForPush()
                AuthState()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AuthState(error = e.userMessage())
            }
        }
    }

    /** Runs [change], shows the message it returns and then reloads the friend lists. */
    private fun changeFriends(change: suspend () -> String?) {
        if (friends.working) return
        friends = friends.copy(working = true)
        launchInSession {
            try {
                change()?.let { _messages.send(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (expireSessionIfUnauthorized(e)) return@launchInSession
                _messages.send(e.userMessage())
            } finally {
                friends = friends.copy(working = false)
            }
            // Also after a failure: it usually means the lists on screen were out of date.
            refreshFriends()
        }
    }

    /** Runs [change], shows the message it returns and then reloads the stats. */
    private fun changeStats(change: suspend () -> String) {
        if (stats.working) return
        stats = stats.copy(working = true)
        launchInSession {
            try {
                _messages.send(change())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (expireSessionIfUnauthorized(e)) return@launchInSession
                _messages.send(e.userMessage())
            } finally {
                stats = stats.copy(working = false)
            }
            refreshStats()
        }
    }

    /**
     * Tells the server where to push this user's notifications. Done on every start because the
     * token can change; failing is fine, the app then just has no push until next time.
     */
    private fun registerForPush() {
        val push = push ?: return
        launchInSession {
            try {
                val token = push.token() ?: return@launchInSession
                api.registerDevice(token, push.platform)
                registeredPushToken = token
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** So a logged-out device doesn't keep showing the previous user's notifications. Best effort. */
    private fun stopPushToThisDevice() {
        val deviceToken = registeredPushToken ?: return
        val authToken = api.token ?: return
        registeredPushToken = null
        // Not a session coroutine: it has to outlive the logout that starts it.
        viewModelScope.launch {
            try {
                api.unregisterDevice(deviceToken, authToken)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** The device's time zone, which decides where the server draws the line between days. */
    private fun timeZone(): String = TimeZone.currentSystemDefault().id

    private fun expireSessionIfUnauthorized(e: Exception): Boolean {
        if (e !is ApiError || e.status != HttpStatusCode.Unauthorized) return false
        logout()
        auth = AuthState(error = "Your session has expired. Please log in again.")
        return true
    }

    private fun Exception.userMessage(): String =
        if (this is ApiError) message else "Could not reach the server. Check your connection and try again."

    private fun newSessionJob(): Job = SupervisorJob(viewModelScope.coroutineContext.job)

    private fun launchInSession(block: suspend () -> Unit): Job = viewModelScope.launch(sessionJob) { block() }

    private companion object {
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
        const val MAX_FEED_ITEMS = 100
        const val RECENT_DRINKS = 30
    }
}
