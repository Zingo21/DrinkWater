package org.erbeenjoyers.drinkwater

private const val INVITE_FRAGMENT = "invite="

/**
 * The link that makes whoever opens it a friend of the user whose invite [code] it is. It opens the web app on
 * [serverUrl], also from the Android app's QR code, and keeps the code in the fragment, which browsers never send to
 * the server, so it stays out of logs on the way.
 */
fun inviteLink(serverUrl: String, code: String): String = "${serverUrl.trimEnd('/')}/#$INVITE_FRAGMENT$code"

/** The invite code in a page's [fragment] (`location.hash`, with or without its `#`), or null if there is none. */
fun inviteCodeFromFragment(fragment: String): String? =
    fragment.removePrefix("#").takeIf { it.startsWith(INVITE_FRAGMENT) }?.removePrefix(INVITE_FRAGMENT)?.takeIf { it.isNotBlank() }
