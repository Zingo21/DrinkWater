package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {
    private val devUrl = "http://10.0.2.2:8080"

    @Test
    fun `release builds start on the release server and can only switch to https`() {
        val config = ServerConfig.forBuild(isDebug = false, devServerUrl = devUrl, releaseServerUrl = "https://drink.example.com/")
        assertEquals("https://drink.example.com", config.url)
        assertTrue(config.editable)
        assertTrue(config.httpsOnly)
    }

    @Test
    fun `a release build without a usable address starts on none`() {
        listOf("", "http://drink.example.com").forEach {
            assertEquals("", ServerConfig.forBuild(isDebug = false, devServerUrl = devUrl, releaseServerUrl = it).url)
        }
    }

    @Test
    fun `debug builds use the development server and can change it`() {
        val config = ServerConfig.forBuild(isDebug = true, devServerUrl = devUrl, releaseServerUrl = "https://drink.example.com")
        assertEquals("http://10.0.2.2:8080", config.url)
        assertTrue(config.editable)
        assertFalse(config.httpsOnly)
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
    fun `https only rejects plain http`() {
        assertEquals("https://drinkwater.example.com", normalizeServerUrl("https://drinkwater.example.com/", httpsOnly = true))
        assertEquals("HTTPS://drinkwater.example.com", normalizeServerUrl("HTTPS://drinkwater.example.com", httpsOnly = true))
        assertNull(normalizeServerUrl("http://192.168.1.20:8080", httpsOnly = true))
    }

    @Test
    fun `stores the picked server`() {
        val store = ServerUrlStore(MapSettings())
        assertNull(store.load())
        store.save("http://192.168.1.20:8080")
        assertEquals("http://192.168.1.20:8080", store.load())
        // A release build can't reach it, so it starts on its own server instead.
        assertNull(store.load(httpsOnly = true))
        store.clear()
        assertNull(store.load())
    }
}
