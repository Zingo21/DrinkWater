package org.erbeenjoyers.drinkwater

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

private const val ACTION_REMIND = "org.erbeenjoyers.drinkwater.REMIND"
private const val CHANNEL_ID = "reminders"
private const val NOTIFICATION_ID = 1

/**
 * Reminders through an alarm that wakes [ReminderReceiver]. The alarm is inexact, which needs no
 * permission; the system may deliver it some minutes late to save battery.
 */
class AndroidReminders(private val context: Context) : ReminderScheduler {
    override fun schedule(at: Instant?) {
        // Whatever reminder is on screen is about a moment that has passed.
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        setAlarm(at)
    }

    internal fun setAlarm(at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            alarms.cancel(intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), intent)
        }
    }
}

/**
 * Shows the reminder when its alarm goes off and sets the alarm for the next one, so reminders
 * keep coming while the app stays closed. Also sets the alarm again after a restart or an app
 * update, both of which clear it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = createSecureSettings(context)
        val reminders = ReminderStore(settings).load()
        // Turned off or logged out since the alarm was set.
        if (!reminders.enabled || SessionStore(settings).load() == null) return

        if (intent.action == ACTION_REMIND) showReminder(context)
        // No drink has been logged on this device since the alarm was set, or the app would have moved it.
        val next = nextReminder(
            reminders,
            now = Clock.System.now(),
            lastDrinkAt = null,
            goalReached = false,
            zone = TimeZone.currentSystemDefault(),
        )
        AndroidReminders(context).setAlarm(next)
    }

    private fun showReminder(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Drink reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("Time for some water")
            .setContentText("It's been a while since you logged a drink.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this does nothing.
        manager.notify(NOTIFICATION_ID, notification)
    }
}
