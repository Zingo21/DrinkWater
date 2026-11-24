package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

/**
 * What `GET /version` answers. [version] is the release the server runs, like `1.2.3`, `main (6ce0990)` for a build
 * of `main`, or `dev` when run from a checkout. The web app comes from the same server, so it is that version too.
 */
@Serializable
data class ServerInfo(val version: String)
