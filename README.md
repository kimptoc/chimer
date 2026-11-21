# Chimer

[![CI](https://github.com/kimptoc/chimer/actions/workflows/ci.yml/badge.svg)](https://github.com/kimptoc/chimer/actions/workflows/ci.yml)

A simple Android countdown-timer app — chimes when time's up, and adapts the chime when your phone is connected to Android Auto.

- Set a timer from the phone (minutes; defaults to your most-recent duration, plus a list of the last five used)
- Pin favourite durations so they always show above the recents; select one and tap "Unpin" to remove it
- A four-note chime + vibration loops on expiry, auto-stopping after 2 minutes if you don't cancel
- While a timer runs, the time left counts down in a status-bar Live Update chip on Android 16+ (when the system allows promoted notifications); older devices get a regular ongoing notification with a Cancel action
- Survives the app being killed and the device being rebooted
- **When Android Auto is connected**, the timer switches to a shorter, distinct chime and auto-stops after 15 seconds instead of 2 minutes. There is no in-car screen — see "Android Auto" below.

## Tech stack

- Kotlin, Jetpack Compose, AndroidX Car App Library (only for `CarConnection`, to detect Android Auto)
- `minSdk = 29` (Android 10), `compileSdk = 35`
- AlarmManager (`USE_EXACT_ALARM`) for scheduling, foreground service (`specialUse`) for the ringing playback
- DataStore Preferences for persistence
- No DI framework — single `AppContainer` for wiring
- 36 JVM unit tests covering the recents and pinned helpers, time formatting, the repository (DataStore via Robolectric), and the ring-profile / hold-to-repeat logic

## Build

You'll need a JDK 17+ — the build uses Android Studio's bundled JBR by default.

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:assembleDebug

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:testDebugUnitTest    # 36 unit tests

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:installDebug
```

Open in Android Studio normally; everything is Gradle Kotlin DSL.

## Android Auto

**The app has no Android Auto screen and won't appear in the Auto launcher.** An earlier version shipped a `CarAppService` (category `POI`), but it never showed up on real head units, even sideloaded with developer mode enabled ([#7](https://github.com/kimptoc/chimer/issues/7)). Android Auto's launcher-visibility rules are host-controlled and restricted to a handful of verticals (navigation, parking, charging, weather, IOT), none of which a generic timer fits.

Instead, the app uses `androidx.car.app.connection.CarConnection` to detect when the phone is connected to Android Auto and plays the shorter, auto-stopping chime (see above). The unreachable car-screen code was removed in [#18](https://github.com/kimptoc/chimer/issues/18).

## Project docs

- `docs/superpowers/specs/2026-05-13-timer-with-auto-design.md` — design spec
- `docs/superpowers/plans/2026-05-13-timer-with-auto-implementation.md` — full implementation plan (20 tasks)
- `docs/MANUAL_SMOKE_TESTS.md` — manual smoke test checklist

## How this was built

This repo was developed with [Claude Code](https://claude.com/claude-code) using the brainstorming → spec → plan → subagent-driven implementation workflow from the `superpowers` plugin. The full design spec, implementation plan, and an honest per-commit history (including the field-debug session that fixed five real Android 14/16 bugs after the initial implementation passed unit tests) are checked in.

## License

MIT — see [LICENSE](LICENSE).
