# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

DrinkWater is a Kotlin Multiplatform app for motivating yourself and your friends to drink water: accounts,
friends, logging drinks, stats, nudges and notifications. Three Gradle modules: `server` (Ktor, JVM),
`shared` (KMP: API models and HTTP client, used by both server and app) and `composeApp` (Compose
Multiplatform UI for Android, iOS and the web via Kotlin/Wasm). `iosApp` is the Xcode entry point; iPhone
users without a paid Apple account use the web app, which the server itself serves. README.md has the full endpoint
table, environment variables and device setup.

## Commands

```
./gradlew :server:run                         # server on :8080 (dev mode, SQLite file drinkwater.db in server/)
./gradlew :server:test                        # server tests (in-process server + real shared client)
./gradlew :server:test --tests 'ApiTest'      # one class; add a method name for one test:
./gradlew :server:test --tests 'ApiTest.changing the password logs out other devices'
./gradlew :composeApp:testDebugUnitTest       # app unit tests (commonTest, run on the JVM)
./gradlew :composeApp:assembleDebug :composeApp:assembleRelease
./gradlew :composeApp:wasmJsBrowserDistribution  # web app; :server:run then serves it at /
./gradlew :composeApp:wasmJsBrowserDevelopmentRun # web dev server on :8081, proxies the API to :8080
./gradlew :server:run --args="reset-password <username>"   # maintenance command, see Commands.kt
```

CI (`.github/workflows/build.yml`) runs exactly the server tests, app tests, debug + release APK builds and
the web build.
Build release too when touching Android dependencies: `lintVitalRelease` only runs there (it once caught an old
Fragment version pulled in by Firebase). iOS can only be built on macOS via Xcode; its Kotlin code in
`iosMain` can't be compiled on Linux, so keep it small and say when it is unverified.

Server config comes from env vars: `JWT_SECRET` (required outside dev mode), `DATABASE_URL`,
`FCM_SERVICE_ACCOUNT_FILE` (push is off without it), `WEB_APP_DIR` (defaults to the web build in this checkout,
if any). The app picks up `composeApp/google-services.json` only if
the file exists.

## Architecture

**Shared contract.** Every request/response type is a `@Serializable` class in `shared/.../api/`, and
`shared/.../client/DrinkWaterApi.kt` is the one client for the whole API. When adding an endpoint, add the DTO
there, the route on the server, and a method on `DrinkWaterApi`; the app only talks to the server through it.
`Notification` is a sealed interface with `@SerialName` discriminators, sent as JSON over the WebSocket and
summarized for push via `describe()`.

**Server layering.** `Application.module(config, push)` wires everything by hand (no DI): `DatabaseFactory`
→ `Db` → services → routes. Routes (`routes/`) are thin; logic and validation live in `services/`, which throw
`ApiException(status, message)` that StatusPages turns into `ErrorResponse`. All DB access goes through
`Db.query { }` (Exposed DSL, runs on Dispatchers.IO). Packages are top-level (`routes`, `services`, `database`,
`auth`, `push`, `config`), not under `org.erbeenjoyers.drinkwater`.

- **Schema changes:** tables are in `database/Tables.kt`. There are no migrations; on startup
  `addMissingColumns` adds new columns to existing databases, so a new column must be nullable or have a default.
  Anything beyond adding columns needs a hand-written migration.
- **Auth:** JWT (`auth/JwtService.kt`) with the user id as subject and a `ver` claim. `Users.tokenVersion` goes up
  on every password change and the `jwt { validate }` block in `Application.kt` rejects tokens whose version
  doesn't match, which also rejects tokens of deleted users. Routes inside `authenticate { }` read the user via
  `call.userId`.
- **Status codes the app depends on:** the app treats any 401 as an expired session and logs the user out, so a
  wrong password while logged in (change password, delete account) is 403, not 401.
- **Notifications:** `NotificationService` sends to a user's open `/notifications` WebSockets (several devices
  per user); if none got it, it pushes to their registered devices through `PushSender` (`FcmPushSender`, or
  `NoPush` when unconfigured) in the background scope. Nudge rate limiting is in memory in `FriendService`.
- **Time zones:** stats, streaks and the leaderboard are computed per request in the caller's IANA zone (`tz`
  parameter / `timeZone` field, default UTC), see `services/Stats.kt`.
- **Web app:** `routes/WebAppRoutes.kt` serves the built web app at `/` (the API routes are more specific and win).
  Browsers can't set headers on a WebSocket, so `/notifications` also accepts the token as the subprotocols
  `drinkwater, <token>` (`NOTIFICATIONS_PROTOCOL`, read by `authHeader` in `Application.kt`).
- **Commands:** `main(args)` runs a maintenance command from `Commands.kt` instead of the server when given
  arguments. They work on the database directly and can run while the server is up.

**Server tests** (`server/src/test`) start the real module with `testApplication` against a temp SQLite file
(`apiTest { client -> }` in `ApiTest.kt`) and mostly drive it through `DrinkWaterApi("http://localhost", client)`,
so they cover the shared client too. `RecordingPush` captures pushes. bcrypt (cost 12) makes each
register/login take ~0.25 s.

**App.** `App.kt` builds a single `AppViewModel` (one big view model holding `auth`, `home`, `stats`, `friends`,
`feed`, `account` state as Compose `mutableStateOf`); screens in `ui/` are stateless and get state + callbacks.
Coroutines for the logged-in user run under `sessionJob` (`launchInSession`) so logging out cancels them all.
Platform pieces are injected as interfaces from `MainActivity` / `MainViewController`: `PushRegistration`,
`ReminderScheduler` (Android only so far), and secure `Settings` (Keystore-encrypted prefs on Android, Keychain on
iOS) used by `SessionStore`, `ReminderStore` and `ServerUrlStore`.

**Web target.** `wasmJsMain/main.kt` uses localStorage for `Settings` and the page's own origin as the server;
there is no push or reminders there yet. Time zones need the `@js-joda/timezone` npm package it references.
`index.html` refuses to load the app without WasmGC (Safari < 18.2). Compose web draws on a canvas, so to check
UI changes drive it with Playwright (Chromium is preinstalled) by tapping coordinates and reading screenshots.

**Server address.** Release builds always use `RELEASE_SERVER_URL` in `composeApp/.../ServerConfig.kt` (a
placeholder `https://` URL until there is a real domain). Debug builds use a dev URL set at build time
(`drinkwater.serverUrl` Gradle property or `local.properties` on Android, `DEV_SERVER_URL` in
`iosApp/Configuration/Config.xcconfig` / untracked `Local.xcconfig` on iOS) and can switch server from the login
screen. Plain HTTP is only allowed in debug (`src/androidDebug/AndroidManifest.xml`, `NSAllowsLocalNetworking`).

## Conventions

- UI text and code comments are in English. KDoc explains why, not what; match the existing density.
- Library versions live in `gradle/libs.versions.toml`; reference them as `libs.*`.
- `drinkwater.db`, `local.properties`, `Local.xcconfig`, `composeApp/google-services.json` and `*service-account*.json` (the FCM server key) are
  gitignored; keep it that way.
