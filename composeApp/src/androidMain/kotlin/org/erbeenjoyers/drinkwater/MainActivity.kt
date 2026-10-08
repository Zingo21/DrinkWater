package org.erbeenjoyers.drinkwater

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.erbeenjoyers.drinkwater.ui.AuthScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = createSecureSettings(applicationContext)
        val push = AndroidPush(applicationContext)
        val reminders = AndroidReminders(applicationContext)
        if (push.isConfigured || ReminderStore(settings).load().enabled) askForNotificationPermission()

        setContent {
            App(
                settings,
                BuildConfig.SERVER_URL,
                push,
                reminders,
                onRemindersEnabled = ::askForNotificationPermission,
            )
        }
    }

    /** From Android 13 notifications stay hidden until the user allows them. The system stops asking after two refusals. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(permission)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    MaterialTheme {
        AuthScreen(AuthState(), onLogin = { _, _ -> }, onRegister = { _, _ -> }, onInputChanged = {})
    }
}
