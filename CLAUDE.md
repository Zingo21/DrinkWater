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

CI (`.github/workflows/build.yml`) runs exactly the server tests, app tests, debug + release APK builds, the web
build and the server image build (published from `main` as `:main`). Releases are `.github/workflows/release.yml`:
a `v1.2.3` tag (a release published on GitHub) runs the tests, builds the APK signed with the keystore from repository
secrets, attaches it to the release and publishes the image as `:1.2.3`, `:1.2` and `:latest` (README, "Releases").
Build release too when touching Android dependencies: `lintVitalRelease` only runs there (it once caught an old
Fragment version pulled in by Firebase). iOS can only be built on macOS via Xcode; its Kotlin code in
`iosMain` can't be compiled on Linux, so keep it small and say when it is unverified.

Server config comes from env vars: `JWT_SECRET` (required outside dev mode), `DATABASE_URL`,
`FCM_SERVICE_ACCOUNT_FILE` (Android push is off without it), `WEB_PUSH_SUBJECT` and `WEB_PUSH_KEYS_FILE` (web push,
off without the subject), `TRUSTED_CLIENT_IP_HEADER` (rate limiting behind a proxy),
`WEB_APP_DIR` (defaults to the web build in this checkout,
if any), `ADMIN_TAILSCALE_USERS`, `ADMIN_PORT` and `ADMIN_HOST` (admin panel, off unless the first is set) and
`DRINKWATER_VERSION` (what `GET /version` reports and the apps show via `versionText`; the image gets it as a build
argument from CI, the release version or `main (<short sha>)`, default `dev`).
The app picks up `composeApp/google-services.json` only if
the file exists.

Deployment (README, "Hosting the server"): CI builds the `Dockerfile` on every run and publishes it to
`ghcr.io/<owner>/drinkwater` (amd64 + arm64): `:main` and `:sha-<commit>` from `main`, `:latest` only from releases,
which is what the Docker host runs. The image only copies
`server/build/install/server` and the web build, so Gradle builds those first, outside Docker, because configuring
the project needs the Android SDK; with no `RUN` steps, arm64 needs no emulation. On the Docker host, `setup.sh`
(run with `curl | bash`) installs Docker, writes `~/drinkwater/{compose.yaml,.env,update.sh}` and optionally a
systemd timer that runs `update.sh` (`docker compose pull` + `up --wait`) every five minutes. `compose.yaml` runs the
image, with the app on the host's 127.0.0.1:8080; the way in is either the Cloudflare Tunnel service (the `cloudflare`
Compose profile, `COMPOSE_PROFILES` in `.env`) or Tailscale Funnel on the host (`tailscale funnel --bg 8080`), chosen
in `setup.sh` and stored as `DRINKWATER_ACCESS`, and `TRUSTED_CLIENT_IP_HEADER` must match it (`CF-Connecting-IP` or
`X-Forwarded-For`, which Funnel overwrites). Host-specific additions go in `compose.override.yaml`, which `setup.sh`
never touches.
The server and `shared` target Java 21 because the image runs a Java 21 JRE. It must stay one container (SQLite file,
WebSocket sessions and rate limits in memory). The image's `HEALTHCHECK` curls `/health` (`routes/HealthRoutes.kt`,
which also queries the database), which `up --wait` waits for.

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
  `call.userId`. Tokens last 30 days, but the app swaps its token through `/auth/refresh` at most once a day while it
  is used (`refreshTokenIfDue`), so only an unused device is logged out. A password change and `/me/logout-others`
  raise the version, forget the user's other devices' push registrations (all but `keepDevice`) and close their
  WebSockets (`endOtherSessions` in `AuthRoutes.kt`).
- **Status codes the app depends on:** the app treats any 401 as an expired session and logs the user out, so a
  wrong password while logged in (change password, delete account) is 403, not 401.
- **Notifications:** `NotificationService` sends to a user's open `/notifications` WebSockets (several devices
  per user); if none got it, it pushes to their registered devices through `PushSender` in the background scope:
  `PlatformPushSender` sends `WEB` devices (token = the browser's subscription JSON) through `WebPushSender` and the
  rest through `FcmPushSender`, each `NoPush` when unconfigured. `ReminderService` runs a loop in the application
scope that each minute pushes the reminders of devices that registered reminder settings (the web app's),
computing due times with the shared `nextReminder` (`shared/.../reminders/`), the same function Android schedules with;
it stores nothing about sent reminders, it checks whether one fell between the previous check and now. Web subscriptions must point at a known push service
  (`WebPushSubscription.parse`), or the server could be made to post anywhere. Nudge rate limiting, and the cooldown on friend request notifications (so a stranger can't send, cancel and resend to flood someone), is in memory in `FriendService` (`PairCooldown`).
- **Invites:** `Users.inviteCode` (128 random bits, base64url, made on first `GET /me/invite`, replaced by
  `/me/invite/reset`); `FriendService.acceptInvite` makes the two friends with no request, accepting one that is pending
  either way, and notifies the inviter (`Notification.InviteAccepted`). The link is `https://<server>/#invite=<code>`
  (`inviteLink`): the fragment never reaches the server, the web app's `main.kt` reads it, removes it from the address
  bar and `AppViewModel` accepts it once someone is logged in. The invite dialog draws the QR code with qrose.
- **Time zones:** stats, streaks and the leaderboard are computed per request in the caller's IANA zone (`tz`
  parameter / `timeZone` field, default UTC), see `services/Stats.kt`.
- **Web app:** `routes/WebAppRoutes.kt` serves the built web app at `/` (the API routes are more specific and win).
  Browsers can't set headers on a WebSocket, so `/notifications` also accepts the token as the subprotocols
  `drinkwater, <token>` (`NOTIFICATIONS_PROTOCOL`, read by `authHeader` in `Application.kt`).
- **Admin panel:** `admin/AdminPanel.kt` is a second Ktor server on `ADMIN_HOST:ADMIN_PORT` (127.0.0.1:8090), started
  from `Application.module` only when `ADMIN_TAILSCALE_USERS` is set. It trusts `tailscale serve` for identity
  (`Tailscale-User-Login`, which serve strips from incoming requests), refuses Funnel traffic
  (`Tailscale-Funnel-Request`) and POSTs without `Sec-Fetch-Site: same-origin`. Queries live in
  `services/AdminService.kt`; changes reuse `UserService`. In Docker it listens on 0.0.0.0 and `compose.yaml`
  publishes it on the VM's 127.0.0.1 only; the Cloudflare tunnel must never point at it (README, "Admin panel").
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
`ReminderScheduler` (Android only; the web app's reminders come from the server), and secure `Settings` (Keystore-encrypted prefs on Android, Keychain on
iOS) used by `SessionStore`, `ReminderStore` and `ServerUrlStore`.

**Theme.** `Theme.kt`: `DrinkWaterTheme` picks Material's `lightColors()` or `darkColors()` from the `ThemeChoice` in
`ThemeStore` (device setting by default, `isSystemInDarkTheme`), chosen under Appearance in the account menu. Use theme
colours, not fixed ones (the QR code's white box is the one exception); `App` wraps everything in a `Surface` so text
outside a `Scaffold` is light in the dark theme. Before Compose draws, `index.html` and Android's `values-night` theme
give the page or window a dark background on a dark device.

**Web target.** `wasmJsMain/main.kt` uses localStorage for `Settings` and the page's own origin as the server.
`WebPush.kt` is both its `PushRegistration` and its `NotificationPrompt` (the "Turn on" banner in `MainScreen`;
browsers only ask for permission from a click), and `resources/sw.js` shows the pushes. Browsers can't schedule a
notification, so the web app's reminders come from the server: `WebPush.remindersFromServer` makes `AppViewModel`
send the device's `ReminderSettings` and time zone with every `registerDevice`.
Playwright's Chromium can't subscribe to a real push service (no Google API keys, and none in incognito contexts). Time zones need the `@js-joda/timezone` npm package it references.
`index.html` refuses to load the app without WasmGC (Safari < 18.2). Safari 27.2 shows a blank white page without
any error because of a WebKit bug with floating-point arguments in tail calls, which every Skia call from Skiko's
`skiko.mjs` is; `composeApp/webpack.config.d/skikoNoTailCall.js` adds a loader (`composeApp/webpack/skikoNoTailCall.cjs`)
that moves those calls into a `try`, and fails the build if a tail-call wrapper reaches the bundle. Remove both once
Safari ships the fix. Compose web draws on a canvas, so to check
UI changes drive it with Playwright (Chromium is preinstalled) by tapping coordinates and reading screenshots.

**Server address.** Release builds start on a URL set at build time so the domain stays out of the repository
(`drinkwater.releaseServerUrl` Gradle property or `local.properties` on Android, where `checkReleaseServerUrl` fails
release builds without an `https://` one and CI passes a placeholder; the version comes from `drinkwater.version` or
the newest `v*` tag, `versionCode` = major·10000 + minor·100 + patch, and `checkReleaseVersion` fails signed builds
without one; `RELEASE_SERVER_URL` in `Local.xcconfig` on iOS)
and can switch to other `https://` servers from the login screen (`ServerConfig.httpsOnly`). Debug builds use a dev URL set at build time
(`drinkwater.serverUrl` Gradle property or `local.properties` on Android, `DEV_SERVER_URL` in
`iosApp/Configuration/Config.xcconfig` / untracked `Local.xcconfig` on iOS) and can switch to any server, HTTP
included. Plain HTTP is only allowed in debug (`src/androidDebug/AndroidManifest.xml`, `NSAllowsLocalNetworking`).

## Conventions

- UI text and code comments are in English. KDoc explains why, not what; match the existing density.
- Library versions live in `gradle/libs.versions.toml`; reference them as `libs.*`.
- `drinkwater.db`, `local.properties`, `Local.xcconfig`, `composeApp/google-services.json` and `*service-account*.json` (the FCM server key) are
  gitignored; keep it that way.
