package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {
    @Test
    fun `release builds always use the release server`() {
        val config = ServerConfig.forBuild(isDebug = false, devServerUrl = "http://10.0.2.2:8080")
        assertEquals(RELEASE_SERVER_URL, config.url)
        assertFalse(config.editable)
        assertTrue(RELEASE_SERVER_URL.startsWith("https://"))
    }

    @Test
    fun `debug builds use the development server and can change it`() {
        val config = ServerConfig.forBuild(isDebug = true, devServerUrl = "http://10.0.2.2:8080")
        assertEquals("http://10.0.2.2:8080", config.url)
        assertTrue(config.editable)
    }

    @Test
    fun `normalizes server urls`() {
        assertEquals("http://192.168.1.20:8080", normalizeServerUrl(" http://192.168.1.20:8080/ "))
        assertEquals("https://api.example.com", normalizeServerUrl("https://api.example.com"))
        assertEquals("HTTP://localhost:8080", normalizeServerUrl("HTTP://localhost:8080"))
        assertEquals("http://[::1]:8080", normalizeServerUrl("http://[::1]:8080"))
    }

    @Test
    fun `rejects what isn't a server root`() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("192.168.1.20:8080"))
        assertNull(normalizeServerUrl("ftp://example.com"))
        assertNull(normalizeServerUrl("http://example.com/api"))
        assertNull(normalizeServerUrl("http://exa mple.com"))
    }

    @Test
    fun `stores the picked server`() {
        val store = ServerUrlStore(MapSettings())
        assertNull(store.load())
        store.save("http://192.168.1.20:8080")
        assertEquals("http://192.168.1.20:8080", store.load())
        store.clear()
        assertNull(store.load())
    }
}
