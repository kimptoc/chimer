# Chimer

[![CI](https://github.com/kimptoc/chimer/actions/workflows/ci.yml/badge.svg)](https://github.com/kimptoc/chimer/actions/workflows/ci.yml)

A simple Android countdown-timer app — chimes when time's up, with an Android Auto cancel surface.

- Set a timer from the phone (minutes; defaults to your most-recent duration, plus a list of the last five used)
- A four-note chime + vibration loops on expiry, auto-stopping after 2 minutes if you don't cancel
- Survives the app being killed and the device being rebooted
- When connected to Android Auto, the running countdown and a big STOP button show on the head unit
- A heads-up alert pops on Android Auto when the alarm fires even if another car app is active

## Tech stack

- Kotlin, Jetpack Compose, AndroidX Car App Library
- `minSdk = 29` (Android 10), `compileSdk = 35`
- AlarmManager (`USE_EXACT_ALARM`) for scheduling, foreground service (`specialUse`) for the ringing playback
- DataStore Preferences for persistence
- No DI framework — single `AppContainer` for wiring
- 21 JVM unit tests covering the recents helper, time formatting, and the repository (DataStore via Robolectric)

## Build

You'll need a JDK 17+ — the build uses Android Studio's bundled JBR by default.

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:assembleDebug

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:testDebugUnitTest    # 21 unit tests

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:installDebug
```

Open in Android Studio normally; everything is Gradle Kotlin DSL.

## Android Auto: known limitations

This app is **not Play-ready** out of the box:

- The `CarAppService` declares `androidx.car.app.category.IOT`. That category is intended for Android Automotive OS (the full car OS), not Android Auto (phone projection). On a production Android Auto head unit it will typically **not appear** in the launcher unless developer mode is enabled. None of the user-visible categories (`NAVIGATION`, `POI`, `MESSAGING`) really fit a timer.
- `TimerCarAppService.createHostValidator()` returns `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`. Useful for development; replace with the strict validator before any production release.

For personal/sideloaded use these are fine. To use it in your own car: enable developer mode in the Android Auto app on your phone, then add this app from "Unknown sources".

## Project docs

- `docs/superpowers/specs/2026-05-13-timer-with-auto-design.md` — design spec
- `docs/superpowers/plans/2026-05-13-timer-with-auto-implementation.md` — full implementation plan (20 tasks)
- `docs/MANUAL_SMOKE_TESTS.md` — manual smoke test checklist for phone + Auto

## How this was built

This repo was developed with [Claude Code](https://claude.com/claude-code) using the brainstorming → spec → plan → subagent-driven implementation workflow from the `superpowers` plugin. The full design spec, implementation plan, and an honest per-commit history (including the field-debug session that fixed five real Android 14/16 bugs after the initial implementation passed unit tests) are checked in.

## License

MIT — see [LICENSE](LICENSE).
