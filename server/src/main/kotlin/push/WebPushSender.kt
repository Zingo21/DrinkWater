package push

import com.interaso.webpush.VapidKeys
import com.interaso.webpush.WebPush
import com.interaso.webpush.WebPushStatusException
import config.WebPushConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import kotlin.io.path.Path

/** A browser's push subscription, as `JSON.stringify(subscription)` gives it; other fields are ignored. */
@Serializable
data class WebPushSubscription(val endpoint: String, val keys: Keys) {
    @Serializable
    data class Keys(val p256dh: String, val auth: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The push services of Chrome and Edge on Android (FCM), Firefox, Safari and Edge on Windows. The server
         * posts to whatever endpoint a subscription names, so it only accepts these, or anyone could make it
         * send requests to any address.
         */
        private val PUSH_SERVICE_HOSTS = listOf(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "push.apple.com",
            "notify.windows.com",
        )

        /** Null unless [token] is a subscription to one of the browsers' push services. */
        fun parse(token: String): WebPushSubscription? {
            val subscription = try {
                json.decodeFromString(serializer(), token)
            } catch (e: IllegalArgumentException) {
                return null
            }
            val uri = try {
                URI(subscription.endpoint)
            } catch (e: URISyntaxException) {
                return null
            }
            val host = uri.host?.lowercase() ?: return null
            if (uri.scheme != "https" || PUSH_SERVICE_HOSTS.none { host == it || host.endsWith(".$it") }) return null
            return subscription
        }
    }
}

/**
 * Pushes to browsers with the Web Push protocol, which the web app uses, including on iPhones once it has
 * been added to the home screen. The VAPID keys that identify this server to the push services are kept
 * in [WebPushConfig.keysFile] and made on first start; browsers subscribed to one key can't be reached
 * with another, so keep the file.
 */
class WebPushSender(config: WebPushConfig) : PushSender {
    private val log = LoggerFactory.getLogger(WebPushSender::class.java)
    private val keys = VapidKeys.load(Path(config.keysFile), generateMissing = true)
    private val webPush = WebPush(config.subject, keys)
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    override val webPushKey: String = Base64.getUrlEncoder().withoutPadding().encodeToString(keys.applicationServerKey)

    override suspend fun send(device: Device, message: PushMessage): PushResult {
        val subscription = WebPushSubscription.parse(device.token) ?: return PushResult.UNREGISTERED
        // The web app's service worker shows this as the notification.
        val payload = buildJsonObject {
            put("title", message.title)
            put("body", message.body)
        }.toString()

        val body = try {
            val decoder = Base64.getUrlDecoder()
            webPush.getBody(payload.toByteArray(), decoder.decode(subscription.keys.p256dh), decoder.decode(subscription.keys.auth))
        } catch (e: RuntimeException) {
            // Keys that don't decode or aren't a valid public key (the library throws all sorts for those):
            // nothing can ever be sent to this subscription.
            log.info("Dropping a web push subscription with unusable keys: {}", e.toString())
            return PushResult.UNREGISTERED
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder(URI(subscription.endpoint))
                    .timeout(TIMEOUT)
                    .apply { webPush.getHeaders(subscription.endpoint, TTL_SECONDS, null, null).forEach(::header) }
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                when (webPush.getSubscriptionState(response.statusCode(), response.body())) {
                    WebPush.SubscriptionState.ACTIVE -> PushResult.SENT
                    WebPush.SubscriptionState.EXPIRED -> PushResult.UNREGISTERED
                }
            } catch (e: WebPushStatusException) {
                log.warn("Web push rejected: {}", e.message)
                PushResult.FAILED
            } catch (e: IOException) {
                log.warn("Could not reach the web push service: {}", e.message)
                PushResult.FAILED
            }
        }
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)

        /** A reminder that a friend drank is stale after a day, so push services may drop it after that. */
        const val TTL_SECONDS = 24 * 60 * 60
    }
}
