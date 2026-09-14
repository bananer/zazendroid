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

`.github/workflows/debug.yml` builds `assembleDebug` and runs `./gradlew test`
on pushes to `main`, pull requests, and manual dispatch. The debug APK is
uploaded as the `app-debug` artifact. No keystore or secrets needed — debug
builds use the SDK-provided debug key. No emulator needed: instrumented tests
(`connectedDebugAndroidTest`) do not run in CI.
