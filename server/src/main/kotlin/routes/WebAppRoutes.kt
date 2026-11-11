package routes

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import java.io.File

/**
 * Serves the web app from [dir] at `/`, next to the API. The API's own routes are more specific,
 * so they win over the files.
 */
fun Route.webApp(dir: File) {
    staticFiles("/", dir) {
        contentType { file ->
            when (file.extension) {
                // Browsers only compile WebAssembly while it streams in when it has this type.
                "wasm" -> ContentType("application", "wasm")
                "webmanifest" -> ContentType("application", "manifest+json")
                else -> null
            }
        }
        cacheControl { file ->
            if (file.nameWithoutExtension.matches(HASHED_NAME)) {
                // The bundler names these after their content, so a new version always gets a new name.
                listOf(CacheControl.MaxAge(maxAgeSeconds = 365 * 24 * 3600, visibility = CacheControl.Visibility.Public))
            } else {
                // index.html, composeApp.js and the rest keep their names, so check for a new version every time.
                listOf(CacheControl.NoCache(null))
            }
        }
    }
}

private val HASHED_NAME = Regex("^[0-9a-f]{20}$")
