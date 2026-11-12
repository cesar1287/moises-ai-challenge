# Architecture and performance audit

## Scope

The review covered startup, Compose state reads, lazy-list reuse, playback, catalog mapping, Room invalidation, and dependency direction. The changes preserve the supplied phone design and offline behavior. This report distinguishes code-level improvements and regression checks from measured frame performance.

## Findings and changes

1. **Playback ticks reached the navigation root.** `MainNavigation` collected the complete playback state, including position updates every 250 ms. Navigation no longer observes that clock. The player entry collects position-free presentation state, while only `PlayerTimeline` collects progress. Its progress animation stays in that composition. A local regression test sends 100 position changes and verifies that presentation state does not emit again; playback status changes still propagate.

2. **Startup constructed unused infrastructure.** The app created Media3 even when the user only browsed Home, and created Retrofit/OkHttp with the repository. The adapter now allocates ExoPlayer only when preparing a playable song. Pause and release leave an unused adapter unallocated. The catalog constructs its network stack lazily on its worker dispatcher. Room still supplies cached Home content, and fully drawn reporting waits until the initial cache observation completes.

3. **Mapping could run on the UI caller's dispatcher.** Catalog mapping, search merging/deduplication, and cache-flow mapping/equality checks now execute on injected worker dispatchers. A test verifies the repository uses its injected dispatcher. Room continues to own database execution and atomic writes. Cancellation remains transparent.

4. **Recording history rewrote song metadata.** Replacing a cached song could invalidate search/album queries and overwrite a newer refresh with an older queue entry. History now inserts metadata only when the song is absent, then updates the recent-play table. An instrumented SQLite regression verifies refreshed metadata survives recording an old queue entry.

5. **Clean Architecture is organized inside one app module.** `domain/` contains plain Kotlin models, repository/player contracts, failures, and queue rules. `data/` implements those contracts with Room, Retrofit, and Media3. `ui/` contains presentation, while the application composition root connects the layers. Domain imports neither Android nor the outer layers, and ViewModels do not import data implementations. Constructor injection and independently tested queue rules preserve the separation through folders and dependency direction.

6. **Release optimization was missing.** Release enables R8/resource shrinking. The separate Macrobenchmark module and its fixture Activity were removed when consolidating to one app module. Performance regression tests and Compose compiler reports remain in the app.

7. **List callbacks captured changing screen state.** Selection callbacks now read the current ViewModel state when invoked, retaining callback identity during text edits. Rows retain stable IDs and declare their content type. Compose compiler reports identify `SongRow`, `PlayerScreen`, `PlayerTimeline`, and navigation as skippable. Collection-wide stability promises were deliberately avoided: strong skipping already compares unchanged list/model references without forcing repeated element equality checks.

## Validation

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug :app:assembleDebug :app:assembleRelease
```

All 29 local tests, including the three plain-Kotlin domain queue tests, now live under `app/src/test`. The 15 instrumented tests remain under `app/src/androidTest`. Room's schema is in `app/schemas`, and Compose reports are under `app/build/reports/compose`.

The app now passes all 31 local tests and all 18 instrumented tests, Android lint, and debug/release/App Bundle builds. The follow-up [Android quality review](android-quality-review.md) records native vector resources, lossless WebP, English/Portuguese localization, and accessibility touch-bound fixes. Before consolidation, a manual walkthrough of the R8-optimized APK also passed: Home → live Daft Punk search → One More Time preview → options sheet → Discovery album and its tracks.

## Earlier measurement attempts

The following evidence predates consolidation. The separate benchmark harness is no longer part of this project; these results do not establish a physical-device performance baseline.

The available test device is an API 37 emulator using software rendering. An initial startup run contained a 95.8-second outlier alongside four samples between 0.98 and 2.36 seconds for initial/fully drawn display. Its trace was subsequently overwritten by the next test invocation, so the outlier's cause is **unresolved**; those samples are not an app performance baseline.

A retained later trace (`SongsBenchmark_coldStartup_iter000_2026-11-12-18-44-02.perfetto-trace`) reports a 1,067 ms cold startup through Perfetto's `android_startups` table. Within that interval, main thread utid 3068 spent 203 ms running, 125 ms runnable, 618 ms sleeping, and 70 ms in uninterruptible sleep. RenderThread slice 4727 (`Creating EGLContext`) spans 366 ms, and main-thread slice 8406 (the first `Choreographer#doFrame`) spans 257 ms. These overlapping wall times must not be added or interpreted as CPU time. This trace does not establish the cause of the earlier outlier.

Reproduce the thread-state breakdown with Perfetto's standard library:

```sql
INCLUDE PERFETTO MODULE sched.time_in_state;
SELECT state, io_wait, blocked_function, dur / 1e6 AS duration_ms
FROM sched_time_in_state_for_thread_in_interval(
    3277388601895, 1067305209, 3068
)
ORDER BY dur DESC;
```

Macrobenchmark 1.4.1 failed fully-drawn metric extraction on this emulator (`No RT frame slice associated with UI thread frame slice ends after reportFullyDrawn`). A retry used 1.5.0 and coordinate gestures to avoid retaining stale accessibility objects during scrolling.

The 1.5.0 emulator retry also failed cold startup: `Target package ... is not running` from `MacrobenchmarkScope.amStartAndWait`. Its log includes successful Home initial/fully drawn reports during preceding launches, followed by benchmark force-stop/launch operations. This does not identify a definitive cause. Startup measurement remains inconclusive; the functional test results are separate from this failed benchmark run.

Scrolling with the revised coordinate gestures was stopped after the prolonged emulator run; it has no completed five-iteration result. The earlier object-based harness failed with a stale accessibility node. No completed physical-device measurement exists for either scenario.

## Remaining limits

The iTunes prefix-pagination cap and repeated response bytes remain API trade-offs, described in the README. Cache eviction/expiration, production background playback, and production telemetry remain future work. Physical-device startup, scrolling, image loading, and audio playback profiling remain outstanding. Functional Media3, offline persistence, navigation, and large-text behavior have separate regression tests.

References: [Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices), [strong skipping and stability](https://developer.android.com/develop/ui/compose/performance/stability/fix), [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
