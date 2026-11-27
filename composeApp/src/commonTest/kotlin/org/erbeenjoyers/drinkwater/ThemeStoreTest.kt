package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeStoreTest {
    @Test
    fun `the theme follows the device until the user picks one, which is kept`() {
        val settings = MapSettings()
        assertEquals(ThemeChoice.System, ThemeStore(settings).load())
        ThemeStore(settings).save(ThemeChoice.Dark)
        assertEquals(ThemeChoice.Dark, ThemeStore(settings).load())
        settings.putString("theme", "Purple")
        assertEquals(ThemeChoice.System, ThemeStore(settings).load())
    }
}
