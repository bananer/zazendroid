# ZazenDroid

There are many meditation apps, but this one is mine.

Mindfully vibecoded.

## Build and run

### Prerequisites

- JDK 21+ to run Gradle (daemon toolchain resolves JDK 25 via Foojay; see
  `gradle/gradle-daemon-jvm.properties`).
- Android SDK with API 37 (`compileSdk`/`targetSdk`; `minSdk` 35).
  Set the SDK location in `local.properties` (gitignored):
  `sdk.dir=/path/to/Android/Sdk`.
- Emulator or device with API 35+ for install/run and instrumented tests.

### Local data server

Plain HTTP to `10.0.2.2` works in **debug** builds only
(`app/src/debug/res/xml/network_security_config.xml`). Release builds set
`android:usesCleartextTraffic="false"` — use HTTPS in production.

### Build

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew assembleRelease        # unsigned; needs signing config to publish
```

### Run

```sh
./gradlew installDebug           # installs de.bananer.zazendroid on attached device/emulator
```

Or open the project in Android Studio and Run `app`. On first launch, confirm
the data-server URL, then browse.

### Tests

```sh
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest   # on emulator/device (app/src/androidTest)
```

### CI

`.github/workflows/build.yml` has two jobs:

- `debug` (every push, PR, manual run): `assembleDebug` + `./gradlew test`,
  uploaded as the `app-debug` artifact. Secret-free.
- `release` (pushes to `main` and `v*` tags, never PRs): signed
  `assembleRelease` via the `production` environment keystore, uploaded as
  `app-release`. The `zazendroid-<ref>.apk` is published as a GitHub Release
  on `v*` tags only — the artifact Obtainium tracks.

Release setup: create a `production` environment (deploy from `main` + tags)
holding `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`;
bump `versionCode`/`versionName` per release and tag `v<versionName>`.
Instrumented tests (`connectedDebugAndroidTest`) do not run in CI.
