package push

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class PushMessage(val title: String, val body: String)

/** A device that gets a user's pushes; for [DevicePlatform.WEB] the token is a browser's push subscription. */
data class Device(val token: String, val platform: DevicePlatform)

enum class PushResult {
    SENT,

    /** The device token is no longer valid (app uninstalled, token rotated) and should be forgotten. */
    UNREGISTERED,
    FAILED,
}

/** Delivers notifications to devices whose app isn't open. */
interface PushSender {
    /** False when push isn't configured, so callers can skip the work of looking up devices. */
    val enabled: Boolean get() = true

    /** The VAPID public key browsers subscribe with, or null when web push is off. */
    val webPushKey: String? get() = null

    suspend fun send(device: Device, message: PushMessage): PushResult

    companion object {
        /**
         * Firebase Cloud Messaging for the native apps if a service account is configured, and Web Push for
         * the web app if it has a subject. Without either, push is off.
         */
        fun fromConfig(config: AppConfig): PushSender {
            val fcm = config.fcmServiceAccountFile?.let { FcmPushSender(File(it)) }
            val web = config.webPush?.let { WebPushSender(it) }
            if (fcm == null && web == null) return NoPush
            return PlatformPushSender(native = fcm ?: NoPush, web = web ?: NoPush)
        }
    }
}

/** Sends to browsers through [web] and to the native apps through [native]. */
class PlatformPushSender(private val native: PushSender, private val web: PushSender) : PushSender {
    override val webPushKey: String? get() = web.webPushKey

    override suspend fun send(device: Device, message: PushMessage): PushResult =
        if (device.platform == DevicePlatform.WEB) web.send(device, message) else native.send(device, message)
}

object NoPush : PushSender {
    override val enabled = false

    override suspend fun send(device: Device, message: PushMessage) = PushResult.FAILED
}

/**
 * Sends through the Firebase Cloud Messaging HTTP v1 API, which reaches both Android and iOS devices.
 * [serviceAccountFile] is the private key JSON downloaded from the Firebase console.
 */
class FcmPushSender(serviceAccountFile: File) : PushSender {
    private val log = LoggerFactory.getLogger(FcmPushSender::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    private val credentials: GoogleCredentials =
        serviceAccountFile.inputStream().use { GoogleCredentials.fromStream(it) }.createScoped(MESSAGING_SCOPE)

    private val sendUri: URI = run {
        val projectId = (credentials as? ServiceAccountCredentials)?.projectId
            ?: error("${serviceAccountFile.path} is not a service account key with a project_id")
        URI("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
    }

    override suspend fun send(device: Device, message: PushMessage): PushResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("message") {
                put("token", device.token)
                putJsonObject("notification") {
                    put("title", message.title)
                    put("body", message.body)
                }
            }
        }.toString()

        try {
            credentials.refreshIfExpired()
            val request = HttpRequest.newBuilder(sendUri)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer ${credentials.accessToken.tokenValue}")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            when {
                response.statusCode() in 200..299 -> PushResult.SENT
                response.statusCode() == 404 || "UNREGISTERED" in response.body() -> PushResult.UNREGISTERED
                else -> {
                    log.warn("FCM rejected a push with status {}: {}", response.statusCode(), response.body())
                    PushResult.FAILED
                }
            }
        } catch (e: IOException) {
            log.warn("Could not reach FCM: {}", e.message)
            PushResult.FAILED
        }
    }

    private companion object {
        const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
