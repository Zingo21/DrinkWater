import config.WebPushConfig
import kotlinx.coroutines.runBlocking
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import push.Device
import push.PlatformPushSender
import push.PushMessage
import push.PushResult
import push.PushSender
import push.WebPushSender
import push.WebPushSubscription
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebPushTest {
    private val dir = createTempDirectory("drinkwater-webpush").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun subscription(endpoint: String) =
        """{"endpoint":"$endpoint","expirationTime":null,"keys":{"p256dh":"BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM","auth":"tBHItJI5svbpez7KI4CCXg"}}"""

    @Test
    fun `accepts subscriptions to the browsers' push services only`() {
        listOf(
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://web.push.apple.com/QGuQyavXut",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://wns2-par02p.notify.windows.com/w/?token=abc",
        ).forEach { assertNotNull(WebPushSubscription.parse(subscription(it)), it) }

        listOf(
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://example.com/push",
            "https://fcm.googleapis.com.example.com/push",
            "https://fcm.googleapis.com@example.com/push",
            "https://localhost:8090/users/1/delete",
            "not a url",
        ).forEach { assertNull(WebPushSubscription.parse(subscription(it)), it) }

        assertNull(WebPushSubscription.parse("not json"))
        assertNull(WebPushSubscription.parse("""{"endpoint":"https://fcm.googleapis.com/fcm/send/abc"}"""))
    }

    @Test
    fun `keeps its VAPID keys between starts`() {
        val config = WebPushConfig("mailto:admin@example.com", File(dir, "vapid.keys").path)
        val key = WebPushSender(config).webPushKey
        // An uncompressed P-256 public key, as browsers want it.
        val bytes = Base64.getUrlDecoder().decode(key)
        assertEquals(65, bytes.size)
        assertEquals(4, bytes[0].toInt())
        assertEquals(key, WebPushSender(config).webPushKey)
    }

    @Test
    fun `refuses a subject push services won't accept`() {
        assertFailsWith<IllegalArgumentException> { WebPushConfig("admin@example.com", "vapid.keys") }
    }

    @Test
    fun `forgets subscriptions it can't use`() = runBlocking {
        val sender = WebPushSender(WebPushConfig("mailto:admin@example.com", File(dir, "vapid.keys").path))
        val message = PushMessage("DrinkWater", "anna drank 250 ml Vatten")
        assertEquals(PushResult.UNREGISTERED, sender.send(Device("not json", DevicePlatform.WEB), message))
        val badKeys = """{"endpoint":"https://fcm.googleapis.com/fcm/send/abc","keys":{"p256dh":"AAAA","auth":"AAAA"}}"""
        assertEquals(PushResult.UNREGISTERED, sender.send(Device(badKeys, DevicePlatform.WEB), message))
    }

    @Test
    fun `sends browsers through web push and apps through Firebase`() = runBlocking {
        val sent = mutableListOf<String>()
        fun recorder(name: String) = object : PushSender {
            override val webPushKey = if (name == "web") "key" else null
            override suspend fun send(device: Device, message: PushMessage): PushResult {
                sent += "$name:${device.token}"
                return PushResult.SENT
            }
        }
        val push = PlatformPushSender(native = recorder("fcm"), web = recorder("web"))
        val message = PushMessage("DrinkWater", "hi")
        push.send(Device("phone", DevicePlatform.ANDROID), message)
        push.send(Device("iphone", DevicePlatform.IOS), message)
        push.send(Device("browser", DevicePlatform.WEB), message)
        assertEquals(listOf("fcm:phone", "fcm:iphone", "web:browser"), sent)
        assertEquals("key", push.webPushKey)
    }
}
