package org.erbeenjoyers.drinkwater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.erbeenjoyers.drinkwater.api.DrinkWaterApi
import org.erbeenjoyers.drinkwater.dto.DrinkDto
import org.erbeenjoyers.drinkwater.dto.FriendDto
import org.erbeenjoyers.drinkwater.dto.NotificationDto
import org.erbeenjoyers.drinkwater.dto.NotificationType
import org.erbeenjoyers.drinkwater.dto.UserDto

/**
 * UI-tillstånd för huvudskärmen.
 */
data class DrinkWaterUiState(
    val currentUserId: Int? = null,
    val currentUser: UserDto? = null,
    val friends: List<FriendDto> = emptyList(),
    val drinks: List<DrinkDto> = emptyList(),
    val selectedDrinkId: Int? = null,
    val notifications: List<NotificationDto> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel som binder ihop UI med [DrinkWaterApi]. Håller den inloggade
 * användaren i [currentUserId] (hämtas från sparade inställningar om möjligt)
 * och exponerar vänner, drycker och notifieringar som StateFlow.
 */
class DrinkWaterViewModel(
    private val api: DrinkWaterApi = DrinkWaterApi(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrinkWaterUiState(isLoading = true))
    val uiState: StateFlow<DrinkWaterUiState> = _uiState.asStateFlow()

    fun loadPersistedUser(userId: Int) {
        _uiState.update { it.copy(currentUserId = userId) }
        loadAll()
    }

    fun createUser(username: String) {
        viewModelScope.launch {
            runCatching { api.createUser(username) }
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(currentUserId = user.id, currentUser = user, message = null)
                    }
                    loadAll()
                }
                .onFailure { e -> setMessage("Kunde inte skapa användare: ${e.localizedMessage}") }
        }
    }

    fun refreshFriends() {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            runCatching { api.getFriends(userId) }
                .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
                .onFailure { setMessage("Kunde inte hämta vänner") }
        }
    }

    fun addFriend(friendUsername: String) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            runCatching { api.addFriend(userId, friendUsername) }
                .onSuccess { refreshFriends(); setMessage("La till vän") }
                .onFailure { setMessage("Kunde inte lägga till vän: ${it.localizedMessage}") }
        }
    }

    fun removeFriend(friendId: Int) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            runCatching { api.removeFriend(userId, friendId) }
                .onSuccess { refreshFriends(); setMessage("Tog bort vän") }
                .onFailure { setMessage("Kunde inte ta bort vän") }
        }
    }

    fun selectDrink(drinkId: Int) {
        _uiState.update { it.copy(selectedDrinkId = drinkId) }
    }

    /**
     * Loggar att användaren har druckit. Servern notifierar i sin tur alla vänner
     * med att användaren har druckit och att det är vännens tur.
     */
    fun drink() {
        val userId = _uiState.value.currentUserId ?: return
        val drinkId = _uiState.value.selectedDrinkId
            ?: _uiState.value.drinks.firstOrNull()?.id
            ?: run {
                setMessage("Ingen dryck vald")
                return
            }
        viewModelScope.launch {
            runCatching { api.logDrink(userId, drinkId) }
                .onSuccess {
                    setMessage("Bra jobbat, du har druckit! \uD83D\uDE0A")
                    refreshFriends()
                    refreshNotifications()
                    refreshUser()
                }
                .onFailure { setMessage("Kunde inte logga dryck: ${it.localizedMessage}") }
        }
    }

    /**
     * Manuellt notifiera en vän att det är dens tur (utöver den automatiska
     * notifiering som sker vid drink()). Användaren kan påminna en vän.
     */
    fun remindFriend(friend: FriendDto) {
        val fromUserId = _uiState.value.currentUserId ?: return
        val fromUsername = _uiState.value.currentUser?.username ?: "Någon"
        viewModelScope.launch {
            runCatching {
                api.sendNotification(
                    fromUserId = fromUserId,
                    toUserId = friend.friendId,
                    request = org.erbeenjoyers.drinkwater.dto.CreateNotificationRequest(
                        fromUserId = fromUserId,
                        toUserId = friend.friendId,
                        type = NotificationType.YOUR_TURN,
                        message = "$fromUsername påminner dig: det är din tur att dricka!"
                    )
                )
            }
            .onSuccess { setMessage("Påminde ${friend.friendUsername}") }
            .onFailure { setMessage("Kunde inte skicka påminnelse") }
        }
    }

    fun refreshNotifications() {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            runCatching { api.getNotifications(userId) }
                .onSuccess { notifications -> _uiState.update { it.copy(notifications = notifications) } }
                .onFailure { setMessage("Kunde inte hämta notifieringar") }
        }
    }

    fun markNotificationRead(notificationId: Int) {
        viewModelScope.launch {
            runCatching { api.markNotificationRead(notificationId) }
                .onSuccess { refreshNotifications() }
                .onFailure { /* ignorerar tyst */ }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadAll() {
        if (_uiState.value.currentUserId == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { api.getDrinks() }
                .onSuccess { drinks ->
                    _uiState.update {
                        it.copy(drinks = drinks, selectedDrinkId = drinks.firstOrNull()?.id)
                    }
                }
            refreshUser()
            refreshFriends()
            refreshNotifications()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun refreshUser() {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            runCatching { api.getUser(userId) }
                .onSuccess { user -> _uiState.update { it.copy(currentUser = user) } }
                .onFailure { /* ignorerar tyst */ }
        }
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }
}
