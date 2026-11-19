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
* `TRUSTED_CLIENT_IP_HEADER` – header a hosting proxy puts the client's address in, e.g. `CF-Connecting-IP` behind a Cloudflare Tunnel. Set it behind
  such a proxy, otherwise all users share one rate limit; never set it without one, or clients can choose their own address.
* `ADMIN_TAILSCALE_USERS` – comma-separated Tailscale logins allowed into the [admin panel](#admin-panel). The panel only runs when this is set.
* `ADMIN_PORT` and `ADMIN_HOST` – where the admin panel listens, default `127.0.0.1:8090`.

Login, registration, password changes and account deletion share a limit of 10 requests a minute per client
address and answer `429 Too Many Requests` beyond that.

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

Any build can also switch server without rebuilding: tap **Server: …** at the bottom of the login screen.

Release builds start on `RELEASE_SERVER_URL` in
`composeApp/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/ServerConfig.kt`, on both Android and iOS, so
friends you give the app to don't have to type anything. They can still switch to another server, but only an
`https://` one: only debug builds allow plain HTTP. `RELEASE_SERVER_URL` is a placeholder until the server has a
real domain.

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

## Signing release builds

Android only installs signed apps, and Play Store updates must be signed with the same key forever, so create one
key, keep it outside the repository and back it up together with its passwords. `keytool` comes with the JDK
(for example `C:\Program Files\Java\jdk-23\bin\keytool.exe` if it is not on your PATH):

```
keytool -genkeypair -v -keystore C:\path\to\drinkwater-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias drinkwater
```

Then add to `local.properties` (untracked):

```
drinkwater.signing.storeFile=C:/path/to/drinkwater-release.jks
drinkwater.signing.storePassword=<keystore password>
drinkwater.signing.keyAlias=drinkwater
drinkwater.signing.keyPassword=<key password>
```

`./gradlew :composeApp:assembleRelease` then writes a signed APK to `composeApp/build/outputs/apk/release/`. The same
keys also work as `-P` Gradle properties. Without them the release build still works but is unsigned, which is what CI builds.

## Hosting the server

The server runs in Docker on any Linux machine, for example a VM on a Proxmox server at home. A
[Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) makes it reachable
over HTTPS from anywhere without opening ports in the router, and also works without a public IP address.
`compose.yaml` runs the server and the tunnel side by side, with the database in a Docker volume.

### Once: the VM

1. Create a Debian or Ubuntu VM in Proxmox (1 CPU and 1 GB of memory are plenty) and install Docker in it:
   ```
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER
   ```
   Log out and in again so the group change applies.
2. Let your Windows machine log in with a key, so deploying doesn't ask for a password (run in PowerShell; skip
   `ssh-keygen` if you already have a key):
   ```
   ssh-keygen -t ed25519
   type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh <user>@<vm-ip> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
   ```

### Once: the tunnel

You need a free Cloudflare account and a domain whose DNS is managed by Cloudflare.

1. In the Cloudflare dashboard go to Zero Trust → Networks → Tunnels and create a tunnel of type Cloudflared.
   Choose Docker as the environment and copy the token from the command it shows (the long string after `--token`).
2. Add a public hostname, for example `drinkwater.example.com`, with service type `HTTP` and URL `server:8080`.
   WebSockets work through the tunnel without extra settings.
3. On the VM, create `~/drinkwater/.env` with the token and a long random JWT secret. Keep the secret: changing it
   later logs everyone out.
   ```
   mkdir -p ~/drinkwater
   echo "JWT_SECRET=$(openssl rand -hex 32)" >> ~/drinkwater/.env
   echo "TUNNEL_TOKEN=<token>" >> ~/drinkwater/.env
   chmod 600 ~/drinkwater/.env
   ```

### Every deploy

From the repository on your Windows machine:

```
.\deploy.ps1 <user>@<vm-ip>
```

It builds the server and the web app, copies them to `~/drinkwater` on the VM and rebuilds and restarts the
containers there. The database volume and `.env` are kept. Check on it with `docker compose logs -f` in `~/drinkwater`.

Finally set `RELEASE_SERVER_URL` in `composeApp/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/ServerConfig.kt` to
`https://<your hostname>` and build the app again. The web app is at the same address.

### Admin panel

The admin panel lists everyone with when they joined, their last drink, drinks in the last week and friends, and
can reset a password or delete an account. Only you can reach it: it is a separate port that is published on the
VM's own 127.0.0.1, and [Tailscale](https://tailscale.com) makes that port reachable from your own devices and
nobody else's. The app itself stays behind the Cloudflare Tunnel as before.

1. Install Tailscale on the VM and log in with your Tailscale account:
   ```
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up
   ```
2. Add the Tailscale logins that may use the panel to `~/drinkwater/.env` (comma-separated; `tailscale status`
   shows yours). Without this line the panel doesn't run.
   ```
   echo "ADMIN_TAILSCALE_USERS=you@example.com" >> ~/drinkwater/.env
   ```
3. Deploy as usual, then make the panel reachable in your tailnet:
   ```
   sudo tailscale serve --bg --https=8443 8090
   ```
   The first time it asks you to turn on HTTPS certificates for the tailnet and prints a link for it.
4. Open `https://<vm-name>.<tailnet>.ts.net:8443/` on a phone or computer that is signed in to Tailscale.

The panel has no login of its own. `tailscale serve` tells it who is asking, in a `Tailscale-User-Login` header it
removes from incoming requests first, and only the logins in `ADMIN_TAILSCALE_USERS` get in. It refuses anything
that came through Tailscale Funnel, and changes must be posted from its own pages, so another site can't make your
browser reset or delete anything. Every reset and deletion is logged with who did it (`docker compose logs server`).

Never add port 8090 as a public hostname in the Cloudflare tunnel: Cloudflare doesn't remove Tailscale's headers,
so anyone could claim to be you.

### Notes

- The server's port isn't published on the VM, so it is only reachable through the tunnel; that is also why it can
  trust the `CF-Connecting-IP` header for rate limiting. To try it on your home network without the tunnel, add
  `ports: ["8080:8080"]` to the `server` service, but remove it again before going live.
- Back up the VM with Proxmox's scheduled backups (Datacenter → Backup). The database is the Docker volume inside it.
- To turn on push, copy the Firebase key to `~/drinkwater/fcm-service-account.json` on the VM and add to the `server`
  service in `compose.yaml`:
  ```
      environment:
        FCM_SERVICE_ACCOUNT_FILE: /secrets/fcm.json
      volumes:
        - ./fcm-service-account.json:/secrets/fcm.json:ro
  ```
- Keep it to one server container: the database, the live notification connections and the rate limits are all local
  to it.
- The image also runs on any other Docker host or service: mount a volume at `/data` and set `JWT_SECRET`.

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
