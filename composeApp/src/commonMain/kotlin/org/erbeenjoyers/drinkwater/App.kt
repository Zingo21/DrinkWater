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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.collectLatest
import org.erbeenjoyers.drinkwater.client.DrinkWaterApi
import org.erbeenjoyers.drinkwater.ui.AuthScreen
import org.erbeenjoyers.drinkwater.ui.MainScreen

/**
 * @param settings secure storage for the login session, see [SessionStore].
 * @param serverUrl root URL of the DrinkWater server, e.g. `http://10.0.2.2:8080`.
 * @param push where this device's push token comes from, or null where push isn't set up.
 */
@Composable
fun App(settings: Settings, serverUrl: String, push: PushRegistration? = null) {
    MaterialTheme {
        val viewModel = viewModel { AppViewModel(DrinkWaterApi(serverUrl), SessionStore(settings), push) }
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
                )
            } else {
                MainScreen(user, viewModel, scaffoldState)
            }
        }
    }
}
