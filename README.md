# DrinkWater

En Kotlin Multiplatform-app (Android & iOS) för att dricka vatten tillsammans
med vänner. Lägg till vänner, logga när du har druckit och notifiera dina vänner
att det är deras tur — allt stöttat av en Ktor-server.

## Projektstruktur

- `/composeApp` — Compose Multiplatform-UI som delas mellan Android och iOS.
  - `commonMain` — gemensam UI-kod (Compose).
  - `androidMain` — Android-specifik startpunkt och lagring.
  - `iosMain` — iOS-specifik startpunkt och lagring.
- `/shared` — Kod som delas mellan app och server.
  - `commonMain/dto` — serialiserbara datamodeller (`UserDto`, `FriendDto`,
    `NotificationDto`, `DrinkDto`).
  - `commonMain/api/DrinkWaterApi.kt` — ktor-klient som appen använder för att
    prata med servern.
  - Plattformsspecifik `ServerConfig` för bas-URL (Android-emulator: `10.0.2.2`,
    iOS-simulator: `localhost`).
- `/server` — Ktor-server (Netty) med SQLite-databas via Exposed.
- `/iosApp` — iOS-applikationens ingångspunkt.

## Funktioner

- **Användare:** skapa/logga in användare (per enhet, sparas lokalt).
- **Drycker:** servern seedar standarddrycker (Vatten, Lemonvatten, Te, Soda).
- **Vänner:** lägg till vän via användarnamn, lista vänner, ta bort vän.
- **Drick & notifiera:** när du loggar en dryck notifieras alla dina vänner:
  1. att du har druckit (`DRANK`), och
  2. att det är deras tur att dricka (`YOUR_TURN`).
- **Påminn:** skicka en extra påminnelse till en vän att det är dennes tur.
- **Notifieringar:** hämta olästa/lästa notifieringar, markera som läst.
- **Realtid:** notifieringar skickas även via WebSocket (`/push/{userId}`) om
  mottagaren är ansluten.

## Köra servern

Servern är byggd med Ktor + Netty + SQLite (Exposed). Starta den med Gradle:

```bash
./gradlew :server:run
```

eller bygg en distribuerad version:

```bash
./gradlew :server:installDist
server/build/install/server/bin/server
```

Servern startar på `http://0.0.0.0:8080`. Databasfilen `drinkwater.db` skapas
i serverns arbetskatalog.

> **Mobilappens bas-URL:** appen pekar på `http://10.0.2.2:8080` (Android-emulator)
> respektive `http://localhost:8080` (iOS-simulator) via `ServerConfig`. Ändra vid
> behov i `shared/.../ServerConfig.*.kt` om servern körs på en annan adress.

## API-översikt

| Metod  | Sökväg                         | Beskrivning                                     |
|--------|-------------------------------|-------------------------------------------------|
| GET    | `/`                           | Health check                                    |
| POST   | `/users`                      | Skapa/hämta användare (body: `{username}`)       |
| GET    | `/users/{id}`                 | Hämta användare                                 |
| GET    | `/drinks`                     | Lista drycker                                   |
| POST   | `/drink`                      | Logga dryck (body: `{userId, drinkId}`)         |
| GET    | `/drinks/logs/{userId}`       | Lista en användares dryckesloggar               |
| GET    | `/friends?userId=`            | Lista vänner                                     |
| POST   | `/friends`                    | Lägg till vän (body: `{userId, friendUsername}`)|
| DELETE | `/friends?userId=&friendId=`  | Ta bort vän                                     |
| GET    | `/notifications?userId=`      | Lista notifieringar                             |
| POST   | `/notifications`              | Skicka notifiering manuellt                     |
| POST   | `/notifications/{id}/read`    | Markera notifiering som läst                    |
| WS     | `/push/{userId}`             | Realtidsnotifieringar via WebSocket             |

## Köra appen

- **Android:** `./gradlew :composeApp:assembleDebug` (kräver Android SDK).
- **iOS:** öppna `iosApp/iosApp.xcodeproj` i Xcode (kräver macOS + Xcode).
- Appen sparar den inloggade användaren lokalt (SharedPreferences/NSUserDefaults)
  så att man slipper logga in varje gång.

## Teknik

- Kotlin Multiplatform + Compose Multiplatform
- Ktor 3.x (server: Netty, klient: OkHttp/Darwin/CIO)
- Exposed + SQLite-jdbc
- kotlinx.serialization
