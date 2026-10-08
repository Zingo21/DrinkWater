This is a Kotlin Multiplatform project targeting Android, iOS, Server.

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

All request and response types live in `shared/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/api`,
so the app and the server use the same models. Endpoints marked 🔒 need an `Authorization: Bearer <token>` header.

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Create an account, returns a token |
| POST | `/auth/login` | Log in, returns a token |
| GET 🔒 | `/me` | The logged-in user |
| GET 🔒 | `/friends` | List friends |
| DELETE 🔒 | `/friends/{friendId}` | Remove a friend |
| GET 🔒 | `/friends/requests` | Incoming and outgoing friend requests |
| POST 🔒 | `/friends/requests` | Send a friend request by username (accepts it if they already asked you) |
| POST 🔒 | `/friends/requests/{id}/accept` | Accept an incoming request |
| DELETE 🔒 | `/friends/requests/{id}` | Decline an incoming or cancel an outgoing request |
| POST 🔒 | `/friends/{friendId}/nudge` | Remind a friend to drink (at most once every five minutes per friend) |
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

## Running the app against the server

Start the server with `./gradlew :server:run`, then run the app. The shared client lives in
`shared/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/client`.

* **Android emulator** – works out of the box; the app talks to `http://10.0.2.2:8080`, the emulator's alias for your machine.
* **Android device** – build with `-Pdrinkwater.serverUrl=http://<your-computer-ip>:8080`.
* **iOS simulator** – uses `http://localhost:8080`.

Only debug builds allow plain HTTP; a release build needs an `https://` server URL.

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
