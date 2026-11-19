@file:OptIn(ExperimentalWasmJsInterop::class)

package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import kotlin.js.Promise

/**
 * Web Push: the browser subscribes with the server's key and the subscription is what the server pushes to.
 * The service worker (sw.js) shows the notifications. [serverKey] fetches the key, or null if the server has
 * web push turned off.
 */
class WebPush(
    private val settings: Settings,
    private val serverKey: suspend () -> String?,
) : PushRegistration, NotificationPrompt {
    override val platform = DevicePlatform.WEB

    /** The subscription as JSON, without ever asking the user: that is [ask], from a click. */
    override suspend fun token(): String? {
        if (!pushSupported() || notificationPermission() != "granted") return null
        return try {
            val key = serverKey() ?: return null
            subscribe(key).await<JsString>().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // E.g. the push service couldn't be reached; the app works without push, so try again next start.
            null
        }
    }

    override suspend fun state(): NotificationPrompt.State {
        if (settings.getBoolean(DISMISSED, false)) return NotificationPrompt.State.HIDDEN
        val canAsk = when {
            iosBrowserTab() -> true
            pushSupported() -> notificationPermission() == "default"
            else -> false
        }
        if (!canAsk || serverKeyOrNull() == null) return NotificationPrompt.State.HIDDEN
        return if (iosBrowserTab()) NotificationPrompt.State.ADD_TO_HOME_SCREEN else NotificationPrompt.State.ASK
    }

    override fun ask(onAnswer: (granted: Boolean) -> Unit) {
        requestNotificationPermission { answer -> onAnswer(answer.toString() == "granted") }
    }

    override fun dismiss() {
        settings.putBoolean(DISMISSED, true)
    }

    private suspend fun serverKeyOrNull(): String? = try {
        serverKey()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val DISMISSED = "notification_prompt_dismissed"
    }
}

private fun pushSupported(): Boolean =
    js("'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window")

/** Safari on an iPhone or iPad outside a home screen app, where web push doesn't exist. */
private fun iosBrowserTab(): Boolean =
    js("/iPhone|iPad|iPod/.test(navigator.userAgent) && !navigator.standalone && !('PushManager' in window)")

private fun notificationPermission(): String = js("Notification.permission")

// Called straight from the click, before any await, or Safari won't show the question.
private fun requestNotificationPermission(onAnswer: (JsString) -> Unit): Unit =
    js("Notification.requestPermission().then(function (answer) { onAnswer(answer); })")

/**
 * The browser's push subscription for [key] (base64url) as JSON, subscribing first if needed. A subscription
 * made with another key (the server's keys were replaced) can't be reached any more, so it is renewed.
 */
private fun subscribe(key: String): Promise<JsString> = js(
    """
    navigator.serviceWorker.ready.then(function (registration) {
        var base64 = key.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - key.length % 4) % 4);
        var bytes = Uint8Array.from(atob(base64), function (c) { return c.charCodeAt(0); });
        return registration.pushManager.getSubscription().then(function (existing) {
            if (existing) {
                var current = existing.options.applicationServerKey;
                var same = current && new Uint8Array(current).join() === bytes.join();
                if (same) return existing;
                return existing.unsubscribe().then(function () {
                    return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes });
                });
            }
            return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes });
        });
    }).then(function (subscription) { return JSON.stringify(subscription); })
    """
)
