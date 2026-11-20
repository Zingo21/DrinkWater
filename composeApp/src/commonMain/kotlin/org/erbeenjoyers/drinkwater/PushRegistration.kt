package org.erbeenjoyers.drinkwater

import org.erbeenjoyers.drinkwater.api.DevicePlatform

/** The platform's side of push notifications: where this device's token comes from. */
interface PushRegistration {
    val platform: DevicePlatform

    /** This device's Firebase Cloud Messaging token, or null when push isn't set up or available. */
    suspend fun token(): String?

    /**
     * Whether the server pushes this device's drink reminders, for platforms that can't show a notification at a
     * set time themselves (browsers). The reminder settings then go along with every registration.
     */
    val remindersFromServer: Boolean get() = false
}

/**
 * Where notifications need the user to say yes from inside the app (browsers), this asks them. Android asks
 * on its own when the app starts, so only the web app has one.
 */
interface NotificationPrompt {
    enum class State {
        /** Nothing to ask: already decided, dismissed, or push isn't possible here. */
        HIDDEN,

        /** On an iPhone in a Safari tab, which can't get notifications until the app is on the home screen. */
        ADD_TO_HOME_SCREEN,

        /** Notifications are possible once the user agrees. */
        ASK,
    }

    suspend fun state(): State

    /** Must run straight from a click: browsers only ask in response to one. */
    fun ask(onAnswer: (granted: Boolean) -> Unit)

    /** "Not now": don't show the prompt again on this device. */
    fun dismiss()
}
