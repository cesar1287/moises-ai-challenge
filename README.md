# Moises Songs

Android app for searching the iTunes catalog, streaming song previews, browsing albums, and revisiting recently played songs.

Built with Kotlin, Jetpack Compose, MVVM, Coroutines, Room, Retrofit, and Media3. A single app module contains `domain`, `data`, and `ui` packages.

## Run

Requires JDK 17, Android SDK 36, and an emulator or device running Android 8.0 (API 26) or newer. Use JDK 17 for both `JAVA_HOME` and Android Studio's Gradle JDK.

```sh
git clone https://github.com/cesar1287/moises-ai-challenge.git
cd moises-ai-challenge
./gradlew :app:installDebug
```

Alternatively, open the project in Android Studio, sync Gradle, and run `app`.

No API key is required. New searches and preview playback need an internet connection. Saved metadata and listening history remain available offline; audio is streamed.

## Tests

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## API pagination

The iTunes API has no supported cursor or offset. The app loads larger prefixes in steps of 25, deduplicates songs, and stops at the API's 200-result limit.
