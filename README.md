This is a Kotlin Multiplatform project targeting Android, iOS, the web and a server.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* `/server` is for the Ktor server application.

* `/shared` is for the code that will be shared between all targets in the project.
  The most important subfolder is `commonMain`. If preferred, you can add code to the platform-specific folders here too.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
## Server API

Run the server with `./gradlew :server:run` (port 8080). Configuration via environment variables:

* `JWT_SECRET` – secret used to sign tokens. Required outside development mode.
* `DATABASE_URL` – JDBC URL, defaults to `jdbc:sqlite:drinkwater.db`.
* `FCM_SERVICE_ACCOUNT_FILE` – path to a Firebase service account key. Optional; enables push notifications.
* `WEB_APP_DIR` – the built web app to serve at `/`. Optional; see [Web app](#web-app-iphones-without-an-apple-developer-account).

All request and response types live in `shared/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/api`,
so the app and the server use the same models. Endpoints marked 🔒 need an `Authorization: Bearer <token>` header.

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Create an account, returns a token |
| POST | `/auth/login` | Log in, returns a token |
| GET 🔒 | `/me` | The logged-in user |
| PUT 🔒 | `/me/password` | Change password; returns a new token and logs out every other device |
| DELETE 🔒 | `/me` | Delete the account and everything in it (the password is asked for again) |
| GET 🔒 | `/friends` | List friends |
| DELETE 🔒 | `/friends/{friendId}` | Remove a friend |
| GET 🔒 | `/friends/requests` | Incoming and outgoing friend requests |
| POST 🔒 | `/friends/requests` | Send a friend request by username (accepts it if they already asked you) |
| POST 🔒 | `/friends/requests/{id}/accept` | Accept an incoming request |
| DELETE 🔒 | `/friends/requests/{id}` | Decline an incoming or cancel an outgoing request |
| POST 🔒 | `/friends/{friendId}/nudge` | Remind a friend to drink (at most once every five minutes per friend) |
| GET 🔒 | `/friends/leaderboard` | You and your friends with today's total, goal and streak, most drunk today first. `tz` as for `/me/stats` |
| GET | `/drinks` | Available drinks |
| POST 🔒 | `/drink` | Log a drink with an optional `amountMl` (default 250) and `timeZone`; friends get notified, also when it takes you past your daily goal |
| GET 🔒 | `/me/drinks` | Your own logs, newest first. Optional `from`, `to` (epoch ms) and `limit` |
| DELETE 🔒 | `/me/drinks/{id}` | Remove one of your logs |
| GET 🔒 | `/me/stats` | Today's total, daily goal, streak and the last seven days. `tz` is an IANA time zone such as `Europe/Stockholm` (default UTC) |
| PUT 🔒 | `/me/goal` | Set your daily goal in ml |
| PUT 🔒 | `/me/devices` | Register this device's FCM token for push notifications |
| DELETE 🔒 | `/me/devices/{token}` | Stop pushing to a device, e.g. on log-out |
| WS 🔒 | `/notifications` | Live notifications as JSON while the app is open |

Run the server tests with `./gradlew :server:test`.

### When a friend is locked out

There is no e-mail, so a forgotten password can't be reset from the app. Whoever runs the server
gives the user a new password instead:

```
./gradlew :server:run --args="reset-password <username>"
```

or, with an installed server (`./gradlew :server:installDist`), `server/build/install/server/bin/server reset-password <username>`.
Run it from the same directory, or with the same `DATABASE_URL`, as the server. It prints a random password,
logs the user out on all devices and can run while the server is up. The user can then log in and pick
their own password under the account menu (the icon at the top right).

## Running the app against the server

Start the server with `./gradlew :server:run`, then run the app. The shared client lives in
`shared/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/client`.

* **Android emulator** – works out of the box; the app talks to `http://10.0.2.2:8080`, the emulator's alias for your machine.
* **Android device** – add `drinkwater.serverUrl=http://<your-computer-ip>:8080` to `local.properties`, or build with `-Pdrinkwater.serverUrl=...`.
* **iOS simulator** – uses `http://localhost:8080`.
* **iPhone** – create `iosApp/Configuration/Local.xcconfig` with `DEV_SERVER_URL = http:/$()/<your-computer-ip>:8080`
  (`//` starts a comment in xcconfig files). The file is not checked in.

Debug builds can also switch server without rebuilding: tap **Server: …** at the bottom of the login screen.

Release builds always talk to `RELEASE_SERVER_URL` in
`composeApp/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/ServerConfig.kt`, on both Android and iOS,
and can't be pointed anywhere else. It is a placeholder until the server has a real domain, and it must be
`https://`: only debug builds allow plain HTTP.

## Web app (iPhones without an Apple developer account)

Without a paid Apple developer account the iOS app can't be shared with friends, so iPhones use the
same Compose UI as a web app instead, built for the browser with Kotlin/Wasm. It needs Safari 18.2
(iOS 18.2) or later; older browsers get a page asking them to update.

```
./gradlew :composeApp:wasmJsBrowserDistribution   # builds into composeApp/build/dist/wasmJs/productionExecutable
./gradlew :server:run                             # serves that build at http://localhost:8080/
```

The server serves the web app itself, so the app always talks to the server it was loaded from. When
running the server from this checkout it finds the build automatically; elsewhere, point `WEB_APP_DIR`
at a copy of it. CI uploads the build as the `drinkwater-web` artifact.

While working on the UI, `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` serves a development build
on http://localhost:8081/ and passes API calls on to the server on port 8080, so start that too.

To install it on an iPhone, open the server's address in Safari, tap Share → **Add to Home Screen**. It
then opens full screen like an app. This needs the server on `https://`.

Browsers can't send an `Authorization` header when they open a WebSocket, so the web app sends its token
for `/notifications` as the subprotocols `drinkwater, <token>` instead. The session is kept in the
browser's localStorage. The web app has no push notifications or reminders yet: notifications only arrive
while it is open.

## Push notifications

A user with the app open gets notifications over the WebSocket. Otherwise the server pushes them
through Firebase Cloud Messaging (FCM). Push is off until you connect a Firebase project:

1. Create a project in the [Firebase console](https://console.firebase.google.com) and add an Android app
   with the package name `org.erbeenjoyers.drinkwater`.
2. Download its `google-services.json` into `composeApp/`. The build picks it up automatically.
3. Under Project settings → Service accounts, generate a private key. Keep the file out of the repository
   and start the server with `FCM_SERVICE_ACCOUNT_FILE` pointing at it.

Without these the app and the server work as before, just without push. The iOS app does not register
for push yet.

## Reminders

The Android app can remind you to drink when you haven't logged anything for a while. Turn it on under
the settings icon in the top bar, where you also choose how long to wait and between which hours
reminders are allowed. Reminders are scheduled on the phone itself, so they need neither the server
nor Firebase, and they stop for the day once you reach your goal. The iOS app has no reminders yet.
