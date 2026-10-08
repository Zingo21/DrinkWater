package org.erbeenjoyers.drinkwater

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import kotlin.coroutines.resume

/**
 * Push through Firebase Cloud Messaging. Firebase shows the notifications itself while the app
 * is in the background; while it is open they arrive over the WebSocket instead.
 */
class AndroidPush(private val context: Context) : PushRegistration {
    override val platform = DevicePlatform.ANDROID

    /** False in builds made without a `google-services.json`, where there is no Firebase project to talk to. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    override suspend fun token(): String? {
        if (!isConfigured) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}
