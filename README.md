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

Android Auto uses the same playback session as the phone. Play a song on the phone to populate Recently played and Albums in the car, or use the car's voice search. Test with a compatible head unit or [Google's Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu). Android Auto renders its own controls; playback continues when the phone screen closes.

## Tests

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## API pagination

The iTunes API has no supported cursor or offset. The app loads larger prefixes in steps of 25, deduplicates songs, and stops at the API's 200-result limit.
