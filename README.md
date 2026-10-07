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
| GET | `/drinks` | Available drinks |
| POST 🔒 | `/drink` | Log a drink; friends get notified |
| WS 🔒 | `/notifications` | Live notifications as JSON while the app is open |

Run the server tests with `./gradlew :server:test`.
