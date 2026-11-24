package org.erbeenjoyers.drinkwater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionTest {
    @Test
    fun `the web app shows the server's version, once it knows it`() {
        assertNull(versionText(appVersion = null, serverVersion = null))
        assertEquals("Version main (6ce0990)", versionText(appVersion = null, serverVersion = "main (6ce0990)"))
    }

    @Test
    fun `the native apps show their own version and the server's`() {
        assertEquals("App 1.0.1", versionText(appVersion = "1.0.1", serverVersion = null))
        assertEquals("App 1.0.1 · server 1.0.2", versionText(appVersion = "1.0.1", serverVersion = "1.0.2"))
    }
}
