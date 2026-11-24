package org.erbeenjoyers.drinkwater

/**
 * The line about versions the app shows under the login form and in the account menu, or null while there is
 * nothing to say. The web app is part of the server, so it only shows the server's [serverVersion]; the native apps
 * also show their own [appVersion], since an older app can talk to a newer server and the other way round.
 */
fun versionText(appVersion: String?, serverVersion: String?): String? = when {
    appVersion == null -> serverVersion?.let { "Version $it" }
    serverVersion == null -> "App $appVersion"
    else -> "App $appVersion · server $serverVersion"
}
