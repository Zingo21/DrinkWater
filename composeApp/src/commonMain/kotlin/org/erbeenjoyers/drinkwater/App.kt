package org.erbeenjoyers.drinkwater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.collectLatest
import org.erbeenjoyers.drinkwater.client.DrinkWaterApi
import org.erbeenjoyers.drinkwater.ui.AuthScreen
import org.erbeenjoyers.drinkwater.ui.MainScreen
import org.erbeenjoyers.drinkwater.ui.ServerChoice

/**
 * @param settings secure storage for the login session, see [SessionStore].
 * @param server which DrinkWater server to talk to, see [ServerConfig.forBuild].
 * @param push where this device's push token comes from, or null where push isn't set up.
 * @param reminders shows the user's own drink reminders, or null where they aren't supported.
 * @param onRemindersEnabled called when the user turns reminders on, e.g. to ask for permission to notify.
 */
@Composable
fun App(
    settings: Settings,
    server: ServerConfig,
    push: PushRegistration? = null,
    reminders: ReminderScheduler? = null,
    onRemindersEnabled: () -> Unit = {},
) {
    MaterialTheme {
        val serverUrls = remember(settings) { ServerUrlStore(settings) }
        var serverUrl by remember { mutableStateOf(if (server.editable) serverUrls.load() ?: server.url else server.url) }
        // Keyed on the server so that picking another one starts over with a fresh client.
        val viewModel = viewModel(key = serverUrl) {
            AppViewModel(DrinkWaterApi(serverUrl), SessionStore(settings), ReminderStore(settings), push, reminders)
        }
        val scaffoldState = rememberScaffoldState()

        LaunchedEffect(viewModel) {
            viewModel.messages.collectLatest { scaffoldState.snackbarHostState.showSnackbar(it) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val user = viewModel.user
            if (user == null) {
                AuthScreen(
                    state = viewModel.auth,
                    onLogin = viewModel::login,
                    onRegister = viewModel::register,
                    onInputChanged = viewModel::clearAuthError,
                    server = if (server.editable) {
                        ServerChoice(
                            current = serverUrl,
                            default = server.url,
                            onChange = { url ->
                                if (url == server.url) serverUrls.clear() else serverUrls.save(url)
                                serverUrl = url
                            },
                        )
                    } else {
                        null
                    },
                )
            } else {
                MainScreen(user, viewModel, scaffoldState, onRemindersEnabled)
            }
        }
    }
}
