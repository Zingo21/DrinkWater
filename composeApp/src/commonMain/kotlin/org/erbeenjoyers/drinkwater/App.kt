package org.erbeenjoyers.drinkwater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
 * @param notificationPrompt asks the user to allow notifications where the platform needs that from inside the app.
 * @param appVersion this app's version, shown next to the server's (see [versionText]); null in the web app, which is
 *   the server's own.
 * @param invite an invite code the app was opened with (see [inviteLink]), accepted once someone is logged in.
 * @param shareLink hands a link to the platform's share sheet, or null where there is none (then the link is copied).
 */
@Composable
fun App(
    settings: Settings,
    server: ServerConfig,
    push: PushRegistration? = null,
    reminders: ReminderScheduler? = null,
    onRemindersEnabled: () -> Unit = {},
    notificationPrompt: NotificationPrompt? = null,
    appVersion: String? = null,
    invite: String? = null,
    shareLink: ((String) -> Unit)? = null,
) {
    val themes = remember(settings) { ThemeStore(settings) }
    var theme by remember { mutableStateOf(themes.load()) }
    DrinkWaterTheme(theme) {
        val serverUrls = remember(settings) { ServerUrlStore(settings) }
        var serverUrl by remember { mutableStateOf(if (server.editable) serverUrls.load(server.httpsOnly) ?: server.url else server.url) }
        // Keyed on the server so that picking another one starts over with a fresh client.
        val viewModel = viewModel(key = serverUrl) {
            AppViewModel(DrinkWaterApi(serverUrl), SessionStore(settings), ReminderStore(settings), push, reminders, invite)
        }
        val scaffoldState = rememberScaffoldState()
        val version = versionText(appVersion, viewModel.serverVersion)

        LaunchedEffect(viewModel) {
            viewModel.messages.collectLatest { scaffoldState.snackbarHostState.showSnackbar(it) }
        }

        // A Surface rather than a background: it also gives the login screen's text the theme's colour, which in the
        // dark theme is light instead of Material's default black.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                                httpsOnly = server.httpsOnly,
                                onChange = { url ->
                                    if (url == server.url) serverUrls.clear() else serverUrls.save(url)
                                    serverUrl = url
                                },
                            )
                        } else {
                            null
                        },
                        version = version,
                    )
                } else {
                    MainScreen(
                        user,
                        viewModel,
                        scaffoldState,
                        onRemindersEnabled,
                        notificationPrompt,
                        version,
                        inviteLink = { code -> inviteLink(serverUrl, code) },
                        shareLink = shareLink,
                        theme = theme,
                        onThemeChange = { choice ->
                            themes.save(choice)
                            theme = choice
                        },
                    )
                }
            }
        }
    }
}
