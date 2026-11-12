# Android quality review

The app remains one Gradle module, with domain, data, and UI package folders. This review covers accessibility, localization, release size, resource loading, and recovery behavior.

## Findings and fixes

| Priority | Finding | Implemented improvement |
| --- | --- | --- |
| High | The custom slider exposed a 16 dp interactive height. Outer minimum-height padding did not enlarge its semantics/touch bounds. | Measure the thumb container at 48 dp high while keeping the visible circle 16 dp. A device test asserts actual touch bounds and exercises the accessibility SetProgress action through to the seek callback. |
| High | Retrying a failed history observation on Home did nothing; clearing a search could also hide that error. | Restart the failed history flow on refresh/retry, retain its failure independently of search, and clear it after successful recovery. A local regression covers failure → search → clear → retry. |
| Medium | UI resources were English-only, without a declared per-app language configuration. | Add complete Brazilian Portuguese resources, retain English as fallback, and generate the system language configuration from resources. All 49 translatable strings have matching format arguments. Catalog titles and artist names remain API data. |
| Medium | Static icons used dynamic asset URLs, asynchronous image requests, and an SVG decoder. | Convert the 12 Figma exports into native VectorDrawables referenced by resource IDs. Original visible path geometry and full-viewport clips are preserved. Remove the SVG decoder and the app's explicit Material icons dependency. A small local vector supplies the close icon. |
| Medium | The placeholder was the largest individual compressed raster in the APK. | Encode it as lossless WebP, preserving all 1024 × 1024 RGBA pixels, including transparent RGB values. Source bytes fall from 583,065 to 281,836. Decoded pixel memory and image resolution are unchanged. |
| Medium | Unused dependency translations occupied the resource table. | Package English and Brazilian Portuguese resources with AGP locale filters. Generated LocaleConfig contains exactly `en` and `pt-BR`. |
| Medium | Pull-to-refresh required a touch gesture. | Add localized refresh accessibility actions to song and album lists. A Portuguese device test invokes the exposed action. |
| Medium | The timeline supplied a percentage rather than a contextual spoken position; its inactive track and options icons had low contrast. | Add localized elapsed/duration state descriptions, raise inactive-track and options-icon contrast, and keep numeric time labels legible in RTL. |
| Low | Time formatting could allocate on each animation recomposition. | Cache numeric labels by whole second and locale, and cache formatted spoken/remaining labels by their inputs. Artwork URL patterns are also compiled once. Playback clock observation remains isolated to the timeline. |
| Low | Back icons did not mirror in RTL, and reopening search did not request input focus. | Auto-mirror the navigation back vector and request focus when the user expands search. Allow headers two lines and retain scrolling player controls at large text sizes. |
| Low | An empty API-26 resource directory generated an obsolete-configuration warning. | Remove the empty folder; launcher resources remain in `mipmap-anydpi` for the API-26 minimum. |

The target sizes and semantics follow [Android accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) and [Compose semantics guidance](https://developer.android.com/develop/ui/compose/accessibility/semantics). System language support uses [AGP's generated per-app language configuration](https://developer.android.com/guide/topics/resources/app-languages); resource filtering uses [localeFilters](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/ApplicationAndroidResources#localeFilters()).

## Release size

This measurement snapshot compares unsigned universal release APKs built with AGP 9.0.1 and the same minimum SDK, shrink settings, and supported ABIs. The project subsequently upgraded to AGP 9.2.1 / Gradle 9.4.1; see the README for current setup. These are file sizes, not Play download estimates or debug/release comparisons.

| Item | Before | After |
| --- | ---: | ---: |
| APK | 2,725,561 bytes | 2374967 bytes |
| Compressed DEX | 1,941,328 bytes | 1892120 bytes |
| Resource table | 156,108 bytes | 26,096 bytes |
| Packaged placeholder | 455,411 bytes | 281,836 bytes |

The APK saves 350594 bytes (**12.86%**). English and Portuguese both ship in this comparison. An Android App Bundle is also built; supported device ABIs are retained.

## R8 review

The size-review baseline used AGP 9.0.1 with R8 9.0.32. Release enables minification, optimized resource shrinking, and `proguard-android-optimize.txt`. There are no app-defined blanket keep rules or flags disabling shrinking, optimization, obfuscation, or full mode. Room, Retrofit, and coroutines supply their consumer rules. No additional keep rules or optimizer switches were needed. Class-retention percentages were not quantified.

See [Android's R8 configuration guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization). The size improvements above come from the actual artifact comparison, not estimated keep-rule scores.

## Validation

- 31 local tests, including locale-aware time formatting and history recovery.
- All 18 device tests pass and cover real Media3 playback, Room persistence/transactions, navigation/restoration, and large-text behavior.
- New checks cover automated player accessibility validation, semantic seeking and 48 dp touch height, Portuguese at 200% text size on a 320 × 480 dp RTL viewport, and a gesture-free Portuguese refresh action.
- All 12 converted icons retain the original visible SVG path geometry. Lossless raster conversion was checked for exact RGBA equality.
- Generated language configuration and translation placeholder parity were checked. A locally signed R8-release smoke test switched the actual Android app-language setting to Portuguese, performed live iTunes search, loaded artwork, and streamed a preview to completion. Restoring the original system-default setting returned the UI to English.
- Debug/release APKs, release App Bundle, and Android lint are built locally. Lint has no errors; remaining advisories concern newer available dependency/toolchain versions.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest
```

Automated accessibility validation uses the [Compose Accessibility Test Framework integration](https://developer.android.com/develop/ui/compose/accessibility/testing) in the test APK only. That check requires API 34 or newer; the app's minimum remains API 26.

## Remaining verification

Manual TalkBack/Switch Access exploration and physical-device CPU, GPU, memory, and frame-time profiling remain necessary. Automated checks do not certify accessibility or establish smooth frame pacing. The available device is an API-37 emulator; earlier emulator timing attempts remain inconclusive, as recorded in [the performance audit](performance.md).

There are 19 dependency/toolchain update advisories from lint. A broader dependency upgrade was not included in these behavior, resource, and accessibility fixes. Cache eviction/expiration and production background playback remain follow-up work. The catalog continues to use the US storefront; changing UI language does not change catalog territory or cached search identity.
