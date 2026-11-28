package org.erbeenjoyers.drinkwater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteTest {
    @Test
    fun `an invite link carries the code in the fragment`() {
        assertEquals("https://drinkwater.example.com/#invite=abc", inviteLink("https://drinkwater.example.com", "abc"))
        assertEquals("https://drinkwater.example.com/#invite=abc", inviteLink("https://drinkwater.example.com/", "abc"))
    }

    @Test
    fun `the web app finds the code in its fragment`() {
        assertEquals("abc", inviteCodeFromFragment("#invite=abc"))
        assertEquals("abc", inviteCodeFromFragment("invite=abc"))
        assertNull(inviteCodeFromFragment(""))
        assertNull(inviteCodeFromFragment("#invite="))
        assertNull(inviteCodeFromFragment("#something-else"))
    }
}
