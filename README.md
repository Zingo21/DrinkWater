# DrinkWater

An app for reminding yourself, and your friends, to drink water. You log what you drink, see how close you are to
your daily goal, and your friends get a notification when you drink, reach your goal or nudge them to have a glass.

- **Accounts** with a username and password, no e-mail needed.
- **Friends**: send and accept friend requests, see a leaderboard of today's totals and streaks.
- **Drinks**: log a glass in one tap, set a daily goal, see the last seven days and your streak.
- **Nudges**: remind a friend to drink (at most once every five minutes per friend).
- **Notifications**: live while the app is open, as push notifications while it is closed.
- **Reminders** when you haven't logged a drink for a while, only during the hours you choose.

It runs as an **Android app** and as a **web app** that iPhones add to their Home Screen (no paid Apple developer
account needed). Both talk to a small **server** you host yourself, for example on a VM at home.

## How it fits together

```
 Android app ──┐
               ├──HTTPS──▶ Cloudflare Tunnel or Tailscale Funnel ──▶ server (Docker on your VM)
 web app ──────┘                                                     ├─ API, WebSocket, push, reminders
 (served by the server)                                              ├─ the web app itself
                                                                     └─ SQLite database
```

Everything is Kotlin. The repository has three Gradle modules:

| Module | What it is |
|---|---|
| `server` | Ktor server: accounts (JWT), friends, drinks, stats, notifications over WebSocket, push to Android (Firebase) and the web (Web Push), reminders for the web app, an admin panel. SQLite through Exposed. |
| `shared` | The API's request and response types and `DrinkWaterApi`, the one HTTP client for it, used by the apps and the server's tests. |
| `composeApp` | The app's UI in Compose Multiplatform, built for Android, the web (Kotlin/Wasm) and iOS. |

`iosApp` is the Xcode project for the native iOS app, which needs a paid Apple developer account to share, so iPhones
normally use the web app. `setup.sh`, `compose.yaml` and the `Dockerfile` run the server on a Docker host.

## How it is built and shipped

- **Every pull request and every change to `main`**: GitHub Actions (`.github/workflows/build.yml`) runs the server
  and app tests and builds the Android APKs, the web app and the server's Docker image. On `main` the image is
  published as `ghcr.io/zingo21/drinkwater:main`, for trying out what is on `main`.
- **Every release** (a tag like `v1.2.3`): `.github/workflows/release.yml` builds the signed APK and attaches it to the
  release, and publishes the image as `ghcr.io/zingo21/drinkwater:latest`. That is what the server runs, so your VM
  only updates when you publish a release, by itself within five minutes. See [Releases](#releases).

## Getting started

- **Run your own server** for you and your friends: [Hosting the server](#hosting-the-server), then
  [Releases](#releases) for the Android app.
- **Work on the code**: start the server with `./gradlew :server:run` and the web app with
  `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (http://localhost:8081), or see
  [Running the app against the server](#running-the-app-against-the-server) for Android and iOS.
  `./gradlew :server:test` runs the server tests; `CLAUDE.md` explains the architecture.

## Hosting the server

The server runs in Docker on any Linux machine, for example a VM on a Proxmox server at home, with the database in a
Docker volume. One of two services makes it reachable over HTTPS from anywhere, without opening ports in the router
and also without a public IP address:

- A [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/): your own
  domain, such as `https://drinkwater.example.com`. Needs a free Cloudflare account and a domain whose DNS Cloudflare
  manages. Cloudflare decrypts the traffic on its way through.
- [Tailscale Funnel](https://tailscale.com/kb/1223/funnel): an address Tailscale gives the VM, such as
  `https://drinkwater.tail1234.ts.net`. Needs only a free Tailscale account, which the [admin panel](#admin-panel)
  uses too, and the traffic stays encrypted all the way to the VM.

Choose before your friends start: the address is built into the Android app, and iPhones have to add the web app to
the Home Screen again and turn notifications on again after it changes. The server itself never needs to know it.

GitHub Actions builds the server and the web app into a Docker image for every [release](#releases), after the tests
pass, and publishes it as `ghcr.io/zingo21/drinkwater`. The VM only needs Docker, `compose.yaml` and a `.env`; it
never sees the source code, Java or Gradle.

### Once: the Cloudflare tunnel (with Cloudflare only)

1. In the Cloudflare dashboard go to Zero Trust → Networks → Tunnels and create a tunnel of type Cloudflared.
   Choose Docker as the environment and copy the token from the command it shows (the long string after `--token`).
2. Add a public hostname, for example `drinkwater.example.com`, with service type `HTTP` and URL `server:8080`.
   WebSockets work through the tunnel without extra settings.

### Once: the VM

1. Create an Ubuntu Server (or Debian) VM in Proxmox: 2 CPUs, 2 GB of memory and 20 GB of disk leave plenty of room.
   Turn on "Start at boot" so the app comes back after a power cut.
2. Log in to it as your own user and run:
   ```
   curl -fsSL https://raw.githubusercontent.com/Zingo21/DrinkWater/main/setup.sh | bash
   ```
   It installs Docker, asks whether to use Cloudflare or Tailscale Funnel, then the tunnel token (Cloudflare) and an
   e-mail address for [web push](#web-app), creates `~/drinkwater/.env` with a random JWT secret, asks whether to
   install updates automatically, and starts the server. With Funnel it also installs Tailscale, shows a link to log
   in with, and the first time a link to allow Funnel and HTTPS certificates in your tailnet; it ends by printing the
   address. Running it again is safe: it keeps `.env`, and so the choice, and the database.
3. Open `https://<your hostname>/health`; it should say `OK`. The web app is at `https://<your hostname>/`.

With Funnel the address comes from the VM's name in Tailscale; rename the machine in the Tailscale admin console,
say to `drinkwater`, before giving the address to anyone.

Keep `JWT_SECRET` in `.env`: changing it later logs everyone out. If the VM can't pull the image (`denied` or
`unauthorized`), the package is private: on GitHub open your profile → Packages → drinkwater → Package settings and
change its visibility to public. The image holds no secrets; those are in `.env`.

Finally set the `RELEASE_SERVER_URL` variable on GitHub to `https://<your hostname>` and publish a
[release](#releases), which also builds the Android app for that address.

### Updates

The VM runs the newest [release](#releases). With automatic updates on, it installs a new one within five minutes of
publishing it. Otherwise, on the VM:

```
~/drinkwater/update.sh
```

It pulls the newest image, restarts the server and waits until it passes its health check, and fails if it
doesn't; then `docker compose logs server` in `~/drinkwater` says why. The database and `.env` are kept, and new
database columns are added on start. Running `setup.sh` again also fetches a newer `compose.yaml`.

To stay on or go back to a version, add `DRINKWATER_IMAGE=ghcr.io/zingo21/drinkwater:1.2.3` to `.env` and run
`update.sh`; remove the line to follow the releases again. `:main` follows every change on `main` instead, and
`:sha-<first 7 characters of a commit>` is any single commit on it. Automatic updates are a systemd
timer: `sudo systemctl disable --now drinkwater-update.timer` turns them off, running `setup.sh` again and answering
yes turns them on.

### Admin panel

The admin panel lists everyone with when they joined, their last drink, drinks in the last week and friends, and
can reset a password or delete an account. Only you can reach it: it is a separate port that is published on the
VM's own 127.0.0.1, and [Tailscale](https://tailscale.com) makes that port reachable from your own devices and
nobody else's. The app itself stays on the Cloudflare Tunnel or Funnel as before.

1. Install Tailscale on the VM and log in with your Tailscale account (already done with Funnel):
   ```
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up
   ```
2. Add the Tailscale logins that may use the panel to `~/drinkwater/.env` (comma-separated; `tailscale status`
   shows yours). Without this line the panel doesn't run.
   ```
   echo "ADMIN_TAILSCALE_USERS=you@example.com" >> ~/drinkwater/.env
   ```
3. Run `~/drinkwater/update.sh` so the server picks it up, then make the panel reachable in your tailnet:
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

- The server's port is only published on the VM's own 127.0.0.1, for Funnel, so from outside it is only reachable
  through the tunnel or Funnel. That is why it can trust their header with the caller's address for rate limiting,
  `TRUSTED_CLIENT_IP_HEADER` in `.env`: `CF-Connecting-IP` from Cloudflare, `X-Forwarded-For` from Funnel, which both
  replace whatever the caller sent. To try it on your home network without either, add `ports: ["8080:8080"]` to the
  `server` service in `compose.override.yaml` (below), but remove it again before going live.
- Switching between Cloudflare and Funnel later: change `DRINKWATER_ACCESS`, `COMPOSE_PROFILES` (`cloudflare` or
  nothing) and `TRUSTED_CLIENT_IP_HEADER` in `.env` to what `setup.sh` writes for the other one, and turn Funnel on or
  off with `sudo tailscale funnel --bg 8080` / `sudo tailscale funnel reset`. Mind the new address, see above.
- To hear when the server is down, point an uptime monitor (UptimeRobot's free plan, for example) at
  `https://<your hostname>/health`. It checks the whole way in, tunnel included. `docker ps` on the VM also shows
  the server as `healthy` or `unhealthy`, but Docker doesn't restart an unhealthy container on its own; it does
  restart one that exits, which the server does when it runs out of memory.
- Back up the VM with Proxmox's scheduled backups (Datacenter → Backup). The database is the Docker volume inside it.
- Changes of your own to the containers go in `~/drinkwater/compose.override.yaml`, which Docker Compose merges into
  `compose.yaml`; `setup.sh` replaces `compose.yaml` but leaves the override alone. For example, to turn on push to
  the Android app, copy the Firebase key to `~/drinkwater/fcm-service-account.json` and create the override with:
  ```
  services:
    server:
      environment:
        FCM_SERVICE_ACCOUNT_FILE: /secrets/fcm.json
      volumes:
        - ./fcm-service-account.json:/secrets/fcm.json:ro
  ```
  then run `update.sh`.
- Keep it to one server container: the database, the live notification connections and the rate limits are all local
  to it.
- The image also runs on any other Docker host or service: mount a volume at `/data` and set `JWT_SECRET`.

## Releases

A release is a version of both the app and the server. Publish one on GitHub and Actions does the rest
(`.github/workflows/release.yml`): it runs the tests, builds the signed APK and attaches it to the release as
`drinkwater-1.2.3.apk`, and publishes the server image as `ghcr.io/zingo21/drinkwater:1.2.3` and `:latest`. VMs with
automatic updates install it within five minutes; the others on `update.sh`.

To make one: on GitHub open Releases → Draft a new release, type a new tag such as `v1.2.3` (higher than the last),
press Generate release notes and Publish. Then send your friends the link to the release; the APK installs over the
previous one and keeps what is on the phone. `git tag v1.2.3 && git push origin v1.2.3` works too.

### Once: the secrets

The workflow needs the signing key and the server address, under the repository's Settings → Secrets and variables →
Actions. Secrets are encrypted and never shown, also in a public repository, but anyone with write access to the
repository could get at them through a workflow, so only give that to people you trust.

- Secrets: `ANDROID_KEYSTORE_BASE64` (the keystore file from [Signing release builds](#signing-release-builds) as
  base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`drinkwater` above) and `ANDROID_KEY_PASSWORD`. To get
  the base64 on Windows, which copies it so you can paste it in:
  ```
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\drinkwater-release.jks")) | Set-Clipboard
  ```
- Variable (the Variables tab): `RELEASE_SERVER_URL`, for example `https://drinkwater.example.com`.

Keep using the same key: phones refuse an update signed with another one. Keep the backup of it too; GitHub won't
give the secret back.

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
keys also work as `-P` Gradle properties. Without them the release build still works but is unsigned, which is what CI
builds. Normally GitHub builds the APK for you, see [Releases](#releases); building it yourself is for trying things out.

The version comes from git tags: `v1.2.3` makes `versionName` 1.2.3 and `versionCode` 10203, which Android compares
to decide whether an APK is an update, so minor and patch stay below 100. A local build takes the newest tag in your
checkout, or `-Pdrinkwater.version=1.2.3`; a signed build without either stops with an error.

## Push notifications

A user with the app open gets notifications over the WebSocket. Otherwise the server pushes them to their
devices: the Android app through Firebase Cloud Messaging (FCM), the web app with Web Push. Each is off until
it is set up.

### Web app

1. Give the server a contact address for the push services. Apple's rejects pushes without one. On the VM:
   ```
   echo "WEB_PUSH_SUBJECT=mailto:you@example.com" >> ~/drinkwater/.env
   ```
   and run `~/drinkwater/update.sh` (`setup.sh` asks for this address, so skip this step if you gave it there).
   The server makes its web push keys on first start and keeps them in the data volume
   (`/data/vapid.keys`). Keep that file: browsers subscribed with one key can't be reached with another.
2. In the web app a banner offers to turn notifications on; tapping **Turn on** asks the browser.
   On an iPhone this only works in the app added to the Home Screen (iOS 16.4 and later), not in a Safari
   tab, and the banner says so there. The Home Screen app has its own storage, so log in again in it.

The server only sends to the browsers' own push services (Google, Mozilla, Apple and Microsoft) and refuses
subscriptions that point anywhere else.

### Android app

1. Create a project in the [Firebase console](https://console.firebase.google.com) and add an Android app
   with the package name `org.erbeenjoyers.drinkwater`.
2. Download its `google-services.json` into `composeApp/`. The build picks it up automatically.
3. Under Project settings → Service accounts, generate a private key. Keep the file out of the repository
   and start the server with `FCM_SERVICE_ACCOUNT_FILE` pointing at it.

Without these the app and the server work as before, just without push. The native iOS app does not
register for push; iPhones get it through the web app.

## Reminders

The Android app and the web app can remind you to drink when you haven't logged anything for a while. Turn it
on under the settings icon in the top bar, where you also choose how long to wait and between which hours
reminders are allowed. They stop for the day once you reach your goal. The settings belong to the device, so a
phone and a computer can remind differently.

- **Android** schedules them on the phone itself, so they need neither the server nor Firebase.
- **The web app** can't: a browser can't show a notification at a set time. It sends its settings and time zone
  to the server along with its push subscription, and the server checks every minute whose reminder is due and
  pushes it (`services/ReminderService.kt`). So they need [web push](#web-app) turned on, notifications allowed
  (on an iPhone in the Home Screen app), and the server running. Reminders due while it was down are skipped.

Both work out the times with the same function, `nextReminder` in `shared`. The native iOS app has no reminders.

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
browser's localStorage. Push notifications work once the server has web push turned on, see
[Push notifications](#push-notifications), and so do [reminders](#reminders).

## Running the app against the server

Start the server with `./gradlew :server:run`, then run the app. The shared client lives in
`shared/src/commonMain/kotlin/org/erbeenjoyers/drinkwater/client`.

* **Android emulator** – works out of the box; the app talks to `http://10.0.2.2:8080`, the emulator's alias for your machine.
* **Android device** – add `drinkwater.serverUrl=http://<your-computer-ip>:8080` to `local.properties`, or build with `-Pdrinkwater.serverUrl=...`.
* **iOS simulator** – uses `http://localhost:8080`.
* **iPhone** – create `iosApp/Configuration/Local.xcconfig` with `DEV_SERVER_URL = http:/$()/<your-computer-ip>:8080`
  (`//` starts a comment in xcconfig files). The file is not checked in.

Any build can also switch server without rebuilding: tap **Server: …** at the bottom of the login screen.

### Server address

Release builds start on a server address set when building, so friends you give the app to don't have to type
anything and the domain stays out of the repository. They can still switch to another server, but only an
`https://` one: only debug builds allow plain HTTP.

* **Android** – [releases](#releases) take it from the `RELEASE_SERVER_URL` variable on GitHub. To build one yourself,
  add `drinkwater.releaseServerUrl=https://<your hostname>` to `local.properties`, or build with
  `-Pdrinkwater.releaseServerUrl=...`. Release builds stop with an error without it.
* **iOS** – add `RELEASE_SERVER_URL = https:/$()/<your hostname>` to `iosApp/Configuration/Local.xcconfig`. Without
  it a release build starts on no server and has to be pointed at one on the login screen.

## Server API

Run the server with `./gradlew :server:run` (port 8080). Configuration via environment variables:

* `JWT_SECRET` – secret used to sign tokens. Required outside development mode.
* `DATABASE_URL` – JDBC URL, defaults to `jdbc:sqlite:drinkwater.db`.
* `FCM_SERVICE_ACCOUNT_FILE` – path to a Firebase service account key. Optional; enables push notifications.
* `WEB_PUSH_SUBJECT` – a `mailto:` address or `https://` URL where push services can reach you. Optional; enables push
  notifications to the web app. `WEB_PUSH_KEYS_FILE` is where the server keeps its web push keys, default `vapid.keys`.
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
| POST 🔒 | `/auth/refresh` | A fresh token for the same session; the app swaps its token at most once a day while in use |
| PUT 🔒 | `/me/password` | Change password; returns a new token and logs out every other device. Optional `keepDevice`: this device's push token, which keeps getting pushes |
| POST 🔒 | `/me/logout-others` | Log out every other device without changing the password; returns a new token. `keepDevice` as above |
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
| PUT 🔒 | `/me/devices` | Register this device for push: its FCM token, or a browser's push subscription (`WEB`). Optional `reminders` and `timeZone` make the server push its drink reminders |
| GET | `/push/web-key` | The key browsers subscribe to web push with; 404 when web push is off |
| DELETE 🔒 | `/me/devices/{token}` | Stop pushing to a device, e.g. on log-out |
| WS 🔒 | `/notifications` | Live notifications as JSON while the app is open |
| GET | `/health` | `OK` when the server is up and can reach its database, otherwise 503. For Docker and uptime monitors |

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

## License

MIT, see `LICENSE`.
